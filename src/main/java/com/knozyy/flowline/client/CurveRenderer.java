package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.curve.CurveGeometry;
import com.knozyy.flowline.network.CurveFlowPayload;
import com.knozyy.flowline.network.CurveViewPayload;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Cached sampled geometry; twelve matte panels, with two narrow open windows. Uses world lighting. */
public final class CurveRenderer {
    private record Tube(List<Vec3> points,List<Vec3[]> rings,List<Vec3[]> normals){}
    private record Mesh(CurveGeometry.Shape shape,List<Vec3> points,Tube body,Tube fluid){}
    private record Collar(Vec3 point,Tube tube,int color){}
    private record Flow(FluidStack fluid,long until){}
    private static final Map<Integer,Mesh> MESHES=new HashMap<>();
    private static final List<Collar> COLLARS=new ArrayList<>();
    private static final Map<Integer,Flow> FLUIDS=new LinkedHashMap<>();
    private static final ResourceLocation SURFACE=new ResourceLocation("flowline","block/curve_surface");
    private CurveRenderer() {}
    public static void clear(){MESHES.clear();COLLARS.clear();FLUIDS.clear();}
    public static List<net.minecraft.world.phys.shapes.VoxelShape> collisions(net.minecraft.world.phys.AABB box){
        List<net.minecraft.world.phys.shapes.VoxelShape> result=new ArrayList<>();
        for(var mesh:MESHES.values())result.addAll(com.knozyy.flowline.curve.CurveCollision.boxes(mesh.shape,box));return result;
    }
    public static void update(){
        MESHES.keySet().retainAll(CurveClient.EDGES.keySet());
        for(var e:CurveClient.EDGES.values()){
            var s=CurveClient.shape(e);var old=MESHES.get(e.id());
            if(s!=null&&(old==null||!s.equals(old.shape))){
                var points=s.points();MESHES.put(e.id(),new Mesh(s,points,geometry(points,CurveGeometry.RADIUS),geometry(points,CurveGeometry.RADIUS*0.75)));
            }
        }
        Map<Integer,List<CurveViewPayload.Edge>> adjacent=new HashMap<>();
        for(var e:CurveClient.EDGES.values()){
            adjacent.computeIfAbsent(e.a(),id->new ArrayList<>()).add(e);
            adjacent.computeIfAbsent(e.b(),id->new ArrayList<>()).add(e);
        }
        COLLARS.clear();
        for(var n:CurveClient.NODES.values()){
            var links=adjacent.getOrDefault(n.id(),List.of());
            if(links.size()<=2&&(n.flags()&1)==0&&n.block()==null)continue;
            int color=n.block()!=null?(n.flags()&2)==0?0x575B60:(n.flags()&4)!=0?0x6D9274:0x7C838B:0x69727A;
            for(var e:links){var mesh=MESHES.get(e.id());if(mesh==null)continue;
                Vec3 dir=e.a()==n.id()?mesh.points.get(1).subtract(n.point()).normalize():mesh.points.get(mesh.points.size()-2).subtract(n.point()).normalize();
                COLLARS.add(new Collar(n.point(),geometry(List.of(n.point(),n.point().add(dir.scale(0.075))),CurveGeometry.RADIUS*1.15),color));
            }
        }
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        FLUIDS.entrySet().removeIf(e->e.getValue().until<mc.level.getGameTime()||!MESHES.containsKey(e.getKey()));
    }
    public static void flow(CurveFlowPayload payload){
        var mc=Minecraft.getInstance();if(mc.level==null)return;int node=payload.from();List<Vec3> path=new ArrayList<>();
        for(int id:payload.edges()){
            var e=CurveClient.EDGES.get(id);var mesh=MESHES.get(id);
            if(e==null||mesh==null||e.a()!=node&&e.b()!=node)return;
            List<Vec3> points=new ArrayList<>(mesh.points);
            if(e.b()==node)Collections.reverse(points);
            if(!path.isEmpty())points.remove(0);path.addAll(points);node=e.a()==node?e.b():e.a();
        }
        if(!payload.item().isEmpty())TravellingItems.addCurve(payload.item(),path);
        if(!payload.fluid().isEmpty()&&FlowlineConfig.Client.RENDER_FLUIDS.get())for(int id:payload.edges()){
            int max=FlowlineConfig.Client.MAX_FLUID_PIPES.get();if(max<=0)break;
            while(FLUIDS.size()>=max&&!FLUIDS.containsKey(id))FLUIDS.remove(FLUIDS.keySet().iterator().next());
            FLUIDS.put(id,new Flow(payload.fluid(),mc.level.getGameTime()+20));
        }
    }
    public static int color(PipeType type){return switch(type){case ITEM->0xCCAE49;case FLUID->0x508DAF;case ENERGY->0xC98548;case UNIVERSAL->0x9275AE;case CHEMICAL->0x80A66E;};}
    public static void render(PoseStack pose,Vec3 camera,float partial){
        var mc=Minecraft.getInstance();if(mc.level==null)return;var buffers=mc.renderBuffers().bufferSource();
        TextureAtlasSprite surface=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SURFACE);
        var body=buffers.getBuffer(RenderType.cutout());
        for(var e:CurveClient.EDGES.values()){
            var mesh=MESHES.get(e.id());if(mesh==null||!mesh.shape.bounds().inflate(48).contains(camera))continue;
            var n=CurveClient.NODES.get(e.a());
            tube(body,pose,camera,mesh.body,color(n.type()),1,surface,true,false);
        }
        for(var collar:COLLARS)if(collar.point.distanceToSqr(camera)<=48*48)tube(body,pose,camera,collar.tube,collar.color,1,surface,false,false);
        buffers.endBatch(RenderType.cutout());
        if(FlowlineConfig.Client.RENDER_FLUIDS.get()){
            var vertices=buffers.getBuffer(RenderType.translucent());
            for(var entry:FLUIDS.entrySet()){
                var mesh=MESHES.get(entry.getKey());if(mesh==null||!mesh.shape.bounds().inflate(FlowlineConfig.Client.FLUID_RENDER_RANGE.get()).contains(camera))continue;
                FluidStack fluid=entry.getValue().fluid;var ext=IClientFluidTypeExtensions.of(fluid.getFluid());
                var sprite=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
                float fade=(float)Math.min(1,(entry.getValue().until-mc.level.getGameTime()-partial)/8.0);
                tube(vertices,pose,camera,mesh.fluid,ext.getTintColor(fluid)&0xFFFFFF,Math.max(0,fade)*0.9f,sprite,false,fluid.getFluid().getFluidType().getLightLevel(fluid)>0);
            }
            buffers.endBatch(RenderType.translucent());
        }
        if(CurveClient.type()!=null&&mc.screen==null&&!mc.player.isShiftKeyDown()){
            var location=CurveClient.location(com.knozyy.flowline.network.CurveActionPayload.Action.ADD,0);
            Vec3 point=location.point().subtract(camera);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),new net.minecraft.world.phys.AABB(point,point).inflate(CurveGeometry.RADIUS),0.65f,0.71f,0.75f,0.8f);
            var hit=CurveClient.pick();
            if(hit!=null&&hit.node()!=0){
                Vec3 selected=CurveClient.NODES.get(hit.node()).point().subtract(camera);
                LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),new net.minecraft.world.phys.AABB(selected,selected).inflate(0.16),0.77f,0.82f,0.85f,1);
            }
            buffers.endBatch(RenderType.lines());
        }
        if(CurveClient.type()!=null&&CurveClient.cursor!=0&&mc.screen==null&&!mc.player.isShiftKeyDown()){
            var n=CurveClient.NODES.get(CurveClient.cursor);if(n!=null){
                var loc=CurveClient.location(com.knozyy.flowline.network.CurveActionPayload.Action.ADD,0);double scale=n.point().distanceTo(loc.point())*0.75;
                Vec3 ta=loc.point().subtract(n.point()).normalize(),tb=ta;
                if(n.block()!=null)ta=Vec3.atLowerCornerOf(n.face().getNormal());
                else {
                    var adjacent=CurveClient.EDGES.values().stream().filter(e->e.a()==n.id()||e.b()==n.id()).toList();
                    if(adjacent.size()==1&&(n.flags()&1)==0){var e=adjacent.get(0);var prev=CurveClient.NODES.get(e.a()==n.id()?e.b():e.a());ta=loc.point().subtract(prev.point()).normalize();}
                }
                if(loc.block()!=null)tb=Vec3.atLowerCornerOf(loc.face().getNormal()).scale(-1);
                var preview=new CurveGeometry.Shape(n.point(),loc.point(),ta.scale(scale),tb.scale(scale));
                var v=buffers.getBuffer(RenderType.lines());var p=preview.points();
                for(int i=1;i<p.size();i++){Vec3 d=p.get(i).subtract(p.get(i-1)).normalize();line(v,pose,camera,p.get(i-1),d);line(v,pose,camera,p.get(i),d);}
                buffers.endBatch(RenderType.lines());
            }
        }
    }
    private static void line(VertexConsumer v,PoseStack pose,Vec3 camera,Vec3 p,Vec3 normal){
        Vec3 r=p.subtract(camera);v.vertex(pose.last().pose(),(float)r.x,(float)r.y,(float)r.z).color(166,182,191,255)
                .normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    private static Tube geometry(List<Vec3> points,double radius){
        final int sides=12;Vec3 previous=null;List<Vec3[]> rings=new ArrayList<>(points.size()),normals=new ArrayList<>(points.size());
        for(int i=0;i<points.size();i++){
            Vec3 tangent=points.get(Math.min(points.size()-1,i+1)).subtract(points.get(Math.max(0,i-1))).normalize();
            Vec3 u=previous==null?tangent.cross(Math.abs(tangent.y)>0.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize():previous.subtract(tangent.scale(previous.dot(tangent))).normalize();
            if(u.lengthSqr()<0.1)u=tangent.cross(new Vec3(0,0,1)).normalize();previous=u;Vec3 w=tangent.cross(u).normalize();
            Vec3[] ring=new Vec3[sides],normal=new Vec3[sides];for(int j=0;j<sides;j++){double a=j*Math.PI*2/sides;normal[j]=u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));ring[j]=points.get(i).add(normal[j].scale(radius));}rings.add(ring);normals.add(normal);
        }
        return new Tube(points,rings,normals);
    }
    private static void tube(VertexConsumer v,PoseStack pose,Vec3 camera,Tube tube,int color,float alpha,TextureAtlasSprite sprite,boolean window,boolean luminous){
        final int sides=12;var points=tube.points;var rings=tube.rings;
        for(int i=1;i<rings.size();i++){
            int light=luminous?0xF000F0:LevelRenderer.getLightColor(Minecraft.getInstance().level,BlockPos.containing(points.get(i)));
            for(int j=0;j<sides;j++){
                if(window&&(j==0||j==6))continue;int k=(j+1)%sides;
                Vec3 normal=tube.normals.get(i)[j];
                vertex(v,pose,camera,rings.get(i-1)[j],normal,color,alpha,sprite.getU0(),sprite.getV0(),light);
                vertex(v,pose,camera,rings.get(i-1)[k],normal,color,alpha,sprite.getU1(),sprite.getV0(),light);
                vertex(v,pose,camera,rings.get(i)[k],normal,color,alpha,sprite.getU1(),sprite.getV1(),light);
                vertex(v,pose,camera,rings.get(i)[j],normal,color,alpha,sprite.getU0(),sprite.getV1(),light);
            }
        }
    }
    private static void vertex(VertexConsumer v,PoseStack pose,Vec3 camera,Vec3 point,Vec3 normal,int color,float alpha,float u,float w,int light){
        v.vertex(pose.last().pose(),(float)(point.x-camera.x),(float)(point.y-camera.y),(float)(point.z-camera.z))
                .color((color>>16)&255,(color>>8)&255,color&255,(int)(255*alpha)).uv(u,w).uv2(light)
                .normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
}
