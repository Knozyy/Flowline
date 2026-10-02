package com.knozyy.flowline.curve;

import com.knozyy.flowline.Flowline;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.*;

@Mod.EventBusSubscriber(modid=Flowline.MODID)
public final class CurveCollision {
    private CurveCollision() {}
    public static List<VoxelShape> shapes(CollisionGetter world,AABB box){
        if(world instanceof ServerLevel level)return CurveData.get(level).collisions(box);
        if(world instanceof Level level&&level.isClientSide){
            List<VoxelShape> shapes=new ArrayList<>();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{
                if(net.minecraft.client.Minecraft.getInstance().level==level)shapes.addAll(com.knozyy.flowline.client.CurveRenderer.collisions(box));
            });return shapes;
        }return List.of();
    }
    public static List<VoxelShape> boxes(CurveGeometry.Shape shape,AABB query){
        if(!shape.bounds().intersects(query))return List.of();
        var points=shape.points();List<VoxelShape> result=new ArrayList<>();
        for(int i=1;i<points.size();i++){
            AABB box=new AABB(points.get(i-1),points.get(i)).inflate(CurveGeometry.RADIUS);
            if(box.intersects(query))result.add(Shapes.create(box));
        }return result;
    }
    @SubscribeEvent public static void blockPlaced(BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var state=event.getPlacedBlock();var shape=state.getCollisionShape(level,event.getPos());if(shape.isEmpty())return;
        for(AABB local:shape.toAabbs())if(!CurveData.get(level).collisions(local.move(event.getPos())).isEmpty()){event.setCanceled(true);return;}
    }
}
