package com.knozyy.flowline.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.item.CurvePipeItem;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.*;
import com.knozyy.flowline.pipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.knozyy.flowline.network.CurveActionPayload.Action;

@Mod.EventBusSubscriber(modid=Flowline.MODID)
public final class CurveServer {
    private record Cursor(ServerLevel level,int node) {}
    private static final Map<ServerPlayer,Cursor> CURSORS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Integer> REQUESTS=new WeakHashMap<>(), ACTIONS=new WeakHashMap<>();
    private CurveServer() {}
    public static CurveViewPayload.Node view(CurveNode n) {
        return new CurveViewPayload.Node(n.id,n.type,n.point,n.block,n.face,(n.joint?1:0)|(n.active?2:0)
                |(n.config!=null&&n.config.side(n.side()).mode==SideMode.EXTRACT?4:0));
    }
    public static CurveViewPayload.Edge view(CurveData d,CurveData.Edge e){var s=d.shape(e);return new CurveViewPayload.Edge(e.id(),e.a(),e.b(),s.ta(),s.tb());}
    private static CurvePicking.Hit aimed(ServerPlayer p,CurveData d) {
        var nodes=d.nodes().values().stream().filter(n->n.point.distanceToSqr(p.getEyePosition())<40*40).map(CurveServer::view).toList();
        var edges=d.edges().values().stream().filter(e->d.shape(e).bounds().inflate(8).contains(p.getEyePosition())).map(e->view(d,e)).toList();
        HitResult block=p.pick(CurveGeometry.REACH,1,false);
        double limit=block.getType()==HitResult.Type.BLOCK?p.getEyePosition().distanceTo(block.getLocation()):CurveGeometry.REACH;
        return CurvePicking.pick(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(CurveGeometry.REACH)),limit,nodes,edges,e->d.shape(d.edges().get(e.id())));
    }
    public static void handle(ServerPlayer p,CurveActionPayload a) {
        if(a.action()==Action.REQUEST){
            int last=REQUESTS.getOrDefault(p,p.tickCount-20);if(p.tickCount-last<20)return;REQUESTS.put(p,p.tickCount);sync(p);return;
        }
        if(a.action()==Action.CANCEL){cancel(p);sync(p);return;}
        int last=ACTIONS.getOrDefault(p,p.tickCount-2);if(p.tickCount-last<2)return;ACTIONS.put(p,p.tickCount);
        if(p.isSpectator()||!p.mayBuild()||!p.isAlive())return;
        CurveData d=CurveData.get(p.serverLevel());CurveNode n=d.nodes().get(a.id());
        boolean holding=p.getMainHandItem().getItem() instanceof CurvePipeItem;
        boolean tool=WrenchItem.isWrench(p.getMainHandItem())||p.getMainHandItem().isEmpty();
        if(!holding&&!tool)return;
        CurvePicking.Hit hit=aimed(p,d);
        if(a.action()!=Action.ADD&&a.action()!=Action.MOVE&&(hit==null||hit.node()!=a.id()&&hit.edge()!=a.id())){sync(p);return;}
        switch(a.action()) {
            case ADD -> {if(holding)add(p,d,a);}
            case SELECT -> {if(holding&&n!=null&&d.mayEdit(p,n)&&((PipeBlock)((CurvePipeItem)p.getMainHandItem().getItem()).getBlock()).type()==n.type){cancel(p);CURSORS.put(p,new Cursor(p.serverLevel(),n.id));}}
            case CONNECT -> {if(holding&&n!=null&&d.mayEdit(p,n))connect(p,d,n,a.finish());}
            case REMOVE_EDGE -> {
                CurveData.Edge e=d.edges().get(a.id());if(e!=null&&owns(p,d.nodes().get(e.a()))&&owns(p,d.nodes().get(e.b()))){
                    refund(p,d.nodes().get(e.a()).type,e.paid());d.removeEdge(e.id());cleanup(d,e.a(),p);cleanup(d,e.b(),p);}
            }
            case REMOVE_NODE -> {if(n!=null&&d.mayEdit(p,n)){
                for(int id:List.copyOf(d.links(n.id))){var e=d.edges().get(id);refund(p,n.type,e.paid());d.removeEdge(id);cleanup(d,CurveData.other(e,n.id),p);}
                dropUpgrades(p,n);d.removeNode(n.id);if(cursor(p,d)==n.id)CURSORS.remove(p);}}
            case INSERT -> {if(holding&&hit!=null&&hit.edge()==a.id())insert(p,d,a.id(),hit.point());}
            case MOVE -> {if(holding&&n!=null&&d.mayEdit(p,n))move(p,d,n,a);}
            case JOINT -> {if(n!=null&&d.mayEdit(p,n)){n.joint=!n.joint;d.invalidate();if(!approve(p,d,d.component(n.id))){n.joint=!n.joint;d.invalidate();}}}
            case MODE -> {if(n!=null&&n.config!=null&&d.mayEdit(p,n)){
                if(!n.active){n.active=true;n.config.side(n.side()).mode=SideMode.INSERT;}
                else if(n.config.side(n.side()).mode==SideMode.INSERT)n.config.side(n.side()).mode=SideMode.EXTRACT;
                else n.active=false;n.config.side(n.side()).resetRuntime();d.changed();}}
            case CONFIG -> {if(n!=null&&n.config!=null&&d.mayEdit(p,n))PipeBlock.openConfig(n.config,n.side(),Conn.ENDPOINT,p);}
            default -> {}
        }
        sync(p);
    }
    private static boolean owns(ServerPlayer p,CurveNode n){return n!=null&&(n.owner.equals(p.getUUID())||p.hasPermissions(2));}
    private static int cursor(ServerPlayer p,CurveData d){Cursor c=CURSORS.get(p);return c!=null&&c.level==p.serverLevel()&&d.nodes().containsKey(c.node)?c.node:0;}
    private static void cancel(ServerPlayer p){Cursor c=CURSORS.remove(p);if(c!=null){CurveData d=CurveData.get(c.level);cleanup(d,c.node,p);}}
    private static void cleanup(CurveData d,int id,ServerPlayer p){CurveNode n=d.nodes().get(id);if(n!=null&&d.links(id).isEmpty()){dropUpgrades(p,n);d.removeNode(id);}}
    private static void dropUpgrades(ServerPlayer p,CurveNode n){if(n.config!=null)for(Direction side:Direction.values())for(ItemStack stack:n.config.removeAllUpgrades(side))p.getInventory().placeItemBackInInventory(stack);}
    private static void error(ServerPlayer p,String key){if(p.connection!=null)p.displayClientMessage(Component.translatable("message.flowline.curve."+key),true);}
    private static boolean location(ServerPlayer p,CurveActionPayload a){
        Vec3 point=a.point();ServerLevel level=p.serverLevel();
        if(!CurveGeometry.finite(point)||p.getEyePosition().distanceTo(point)>CurveGeometry.REACH||!level.isLoaded(BlockPos.containing(point))
                ||level.isOutsideBuildHeight(BlockPos.containing(point))||!level.getWorldBorder().isWithinBounds(BlockPos.containing(point))
                ||!level.mayInteract(p,BlockPos.containing(point)))return false;
        Vec3 toward=point.subtract(p.getEyePosition());
        if(toward.normalize().dot(p.getLookAngle())<0.90)return false;
        if(a.block()!=null){
            if(!level.isLoaded(a.block())||!level.mayInteract(p,a.block()))return false;
            HitResult hit=p.pick(CurveGeometry.REACH,1,false);
            if(!(hit instanceof BlockHitResult bh)||hit.getType()!=HitResult.Type.BLOCK||!bh.getBlockPos().equals(a.block())||bh.getDirection()!=a.face()
                    ||bh.getLocation().add(Vec3.atLowerCornerOf(a.face().getNormal()).scale(0.10)).distanceTo(point)>0.26)return false;
        }
        var obstruction=level.clip(new ClipContext(p.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return obstruction.getType()!=HitResult.Type.BLOCK || obstruction.getLocation().distanceTo(point)<0.14;
    }
    private static void add(ServerPlayer p,CurveData d,CurveActionPayload a){
        if(!location(p,a)){error(p,"blocked");return;}
        PipeType type=((PipeBlock)((CurvePipeItem)p.getMainHandItem().getItem()).getBlock()).type();int start=cursor(p,d);
        if(start!=0&&d.nodes().get(start).type!=type){cancel(p);start=0;}
        CurveNode n=d.addNode(type,p.getUUID(),a.point(),a.block(),a.face());if(n==null){error(p,"limit");return;}
        if(start==0&&n.config!=null)n.config.side(n.side()).mode=SideMode.EXTRACT;
        if(start!=0){
            var edge=d.connect(start,n.id,0);
            if(edge==null){d.removeNode(n.id);error(p,"limit");return;}
            if(!approve(p,d,d.component(start))){d.removeNode(n.id);return;}
        }
        CURSORS.put(p,new Cursor(p.serverLevel(),n.id));if(a.finish()&&start!=0)cancel(p);
    }
    private static void connect(ServerPlayer p,CurveData d,CurveNode n,boolean finish){
        int start=cursor(p,d);if(start==0){CURSORS.put(p,new Cursor(p.serverLevel(),n.id));return;}
        var edge=d.connect(start,n.id,0);
        if(edge==null)return;
        if(!approve(p,d,d.component(start))){d.removeEdge(edge.id());return;}
        CURSORS.put(p,new Cursor(p.serverLevel(),n.id));if(finish)cancel(p);
    }
    private static void move(ServerPlayer p,CurveData d,CurveNode n,CurveActionPayload a){
        if(!location(p,a))return;
        Vec3 old=n.point;BlockPos oldBlock=n.block;Direction oldFace=n.face;CurveNode.Endpoint oldConfig=n.config;
        if(oldConfig!=null&&a.block()==null){error(p,"detach");return;}
        n.point=a.point();n.block=a.block();n.face=a.face();
        if(n.block!=null){
            n.config=new CurveNode.Endpoint(d,n,p.serverLevel());
            if(oldConfig!=null){
                n.config.load(oldConfig.saveWithoutMetadata());
                Direction oldSide=oldFace.getOpposite();
                if(oldSide!=n.side()){
                    n.config.side(n.side()).load(oldConfig.side(oldSide).save());
                    for(int i=0;i<SideConfig.UPGRADE_SLOTS;i++){
                        n.config.upgrades().setItem(PipeBlockEntity.upgradeSlot(oldSide,i),ItemStack.EMPTY);
                        n.config.upgrades().setItem(PipeBlockEntity.upgradeSlot(n.side(),i),oldConfig.getUpgrade(oldSide,i).copy());
                    }
                }
            }
        }
        d.invalidate();
        if(!approve(p,d,d.component(n.id))){if(n.config!=null&&n.config!=oldConfig)n.config.setRemoved();n.point=old;n.block=oldBlock;n.face=oldFace;n.config=oldConfig;d.invalidate();}
        else if(oldConfig!=null)oldConfig.setRemoved();
    }
    private static void insert(ServerPlayer p,CurveData d,int id,Vec3 point){
        if(d.edges().size()>=CurveData.MAX_EDGES){error(p,"limit");return;}
        var e=d.edges().get(id);if(e==null)return;CurveNode a=d.nodes().get(e.a()),b=d.nodes().get(e.b());
        if(!owns(p,a)||!owns(p,b))return;
        var n=d.addNode(a.type,p.getUUID(),point,null,Direction.UP);if(n==null)return;
        d.removeEdge(id);var first=d.connect(a.id,n.id,0);var second=d.connect(n.id,b.id,0);
        if(first==null||second==null){d.removeNode(n.id);d.restoreEdge(e);error(p,"limit");return;}
        int firstUnits=Math.min(e.units(),(int)Math.ceil(d.shape(first).length()));
        int firstPaid=Math.min(e.paid(),firstUnits);
        d.setBudget(first,firstPaid,firstUnits);d.setBudget(second,e.paid()-firstPaid,e.units()-firstUnits);
        if(!approve(p,d,d.component(n.id))){d.removeNode(n.id);d.restoreEdge(e);}
    }
    private static boolean approve(ServerPlayer p,CurveData d,Set<Integer> component){
        if(!valid(d,component)||!allowed(p,d,component)){error(p,"blocked");return false;}
        return pay(p,d,component);
    }
    /** Validate all affected curves: degree-two tangents can also change the neighbouring edges. */
    public static boolean valid(CurveData d,Set<Integer> component){
        if(component.size()>FlowlineConfig.MAX_NETWORK_SIZE.get())return false;
        for(int id:component){
            var n=d.nodes().get(id);var adjacent=d.links(id).stream().map(d.edges()::get).toList();
            if(adjacent.size()>8)return false;
            List<Vec3> directions=new ArrayList<>();
            for(var e:adjacent){var points=d.shape(e).points();directions.add((e.a()==id?points.get(1):points.get(points.size()-2)).subtract(n.point).normalize());}
            for(int i=0;i<directions.size();i++)for(int j=i+1;j<directions.size();j++)if(directions.get(i).dot(directions.get(j))>Math.cos(Math.toRadians(20)))return false;
        }
        for(var e:d.edges().values()){
            if(!component.contains(e.a()))continue;var s=d.shape(e);double length=s.length();
            if(length<0.2||length>CurveGeometry.MAX_LENGTH)return false;
            List<Vec3> points=s.points();
            for(int i=1;i<points.size();i++){
                AABB box=new AABB(points.get(i-1),points.get(i)).inflate(CurveGeometry.RADIUS*0.92);
                for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
                    ServerLevel level=level(d);if(!level.isLoaded(pos)||level.isOutsideBuildHeight(pos))return false;
                    for(AABB block:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())if(block.move(pos).intersects(box))return false;
                }
            }
            for(var other:d.edges().values()){
                if(other.id()==e.id()||other.id()<e.id()&&component.contains(other.a()))continue;
                var os=d.shape(other);if(!s.bounds().intersects(os.bounds()))continue;
                boolean shared=e.a()==other.a()||e.a()==other.b()||e.b()==other.a()||e.b()==other.b();
                var op=os.points();
                for(Vec3 p:points){
                    if(shared&&((e.a()==other.a()||e.b()==other.a())&&p.distanceTo(d.nodes().get(other.a()).point)<0.28
                            ||(e.a()==other.b()||e.b()==other.b())&&p.distanceTo(d.nodes().get(other.b()).point)<0.28))continue;
                    for(int j=1;j<op.size();j++)if(CurveGeometry.distanceToSegment(p,op.get(j-1),op.get(j))<CurveGeometry.RADIUS*1.8)return false;
                }
            }
        }
        return true;
    }
    private static ServerLevel level(CurveData d){return d.level();}
    private static boolean allowed(ServerPlayer p,CurveData d,Set<Integer> component){
        Set<BlockPos> positions=new HashSet<>();
        for(var e:d.edges().values())if(component.contains(e.a())){
            var samples=d.shape(e).points();
            for(int i=1;i<samples.size();i++){
                var box=new AABB(samples.get(i-1),samples.get(i)).inflate(CurveGeometry.RADIUS);
                for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))positions.add(pos.immutable());
            }
        }
        for(BlockPos pos:positions)if(!p.serverLevel().getWorldBorder().isWithinBounds(pos)||!p.serverLevel().mayInteract(p,pos)
                ||ForgeEventFactory.onBlockPlace(p,BlockSnapshot.create(p.level().dimension(),p.level(),pos),Direction.UP))return false;
        return true;
    }
    private static boolean pay(ServerPlayer p,CurveData d,Set<Integer> component){
        List<CurveData.Edge> affected=d.edges().values().stream().filter(e->component.contains(e.a())).toList();
        int cost=affected.stream().mapToInt(e->Math.max(0,(int)Math.ceil(d.shape(e).length())-e.units())).sum();
        if(cost==0)return true;var item=CurveData.block(d.nodes().get(affected.get(0).a()).type).asItem();
        if(!p.getAbilities().instabuild){
            int available=0;for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(item))available+=stack.getCount();}
            if(available<cost){error(p,"materials");return false;}
            for(int i=0;i<p.getInventory().getContainerSize()&&cost>0;i++){var stack=p.getInventory().getItem(i);if(stack.is(item)){int take=Math.min(cost,stack.getCount());stack.shrink(take);cost-=take;}}
            p.getInventory().setChanged();
        }
        d.updatePaid(affected,p.getAbilities().instabuild);
        return true;
    }
    private static void refund(ServerPlayer p,PipeType type,int amount){if(amount>0&&!p.getAbilities().instabuild)p.getInventory().placeItemBackInInventory(new ItemStack(CurveData.block(type),amount));}
    public static void sync(ServerPlayer p){
        if(p.connection==null)return;
        CurveData d=CurveData.get(p.serverLevel());Cursor c=CURSORS.get(p);
        if(c!=null&&(c.level!=p.serverLevel()||!(p.getMainHandItem().getItem() instanceof CurvePipeItem))){cancel(p);c=null;}
        List<CurveViewPayload.Edge> edges=new ArrayList<>();Map<Integer,CurveViewPayload.Node> nodes=new LinkedHashMap<>();boolean limited=false;
        for(var e:d.edges().values()){
            if(!d.shape(e).bounds().inflate(48).contains(p.position()))continue;
            if(edges.size()>=CurveViewPayload.MAX_EDGES){limited=true;break;}
            edges.add(view(d,e));nodes.put(e.a(),view(d.nodes().get(e.a())));nodes.put(e.b(),view(d.nodes().get(e.b())));
        }
        int cursor=cursor(p,d);if(cursor!=0&&nodes.size()<CurveViewPayload.MAX_NODES)nodes.put(cursor,view(d.nodes().get(cursor)));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new CurveViewPayload(p.level().dimension().location(),cursor,List.copyOf(nodes.values()),edges,limited));
    }
    private static long animationTick;private static int animations;
    public static void animate(CurveData d,CurveNode n,PipeNetwork.Target target,ItemStack item,FluidStack fluid){
        if(!FlowlineConfig.SEND_ANIMATIONS.get()&&!item.isEmpty()||fluid!=null&&!FlowlineConfig.SEND_FLUID_ANIMATIONS.get())return;
        ServerLevel level=d.level();if(level.getNearestPlayer(n.point.x,n.point.y,n.point.z,48,false)==null)return;
        if(animationTick!=level.getGameTime()){animationTick=level.getGameTime();animations=0;}if(animations++>=64)return;
        CurveNode dest=d.nodes().values().stream().filter(node->node.config==target.pipe()).findFirst().orElse(null);if(dest==null)return;
        var route=d.route(n.id,dest.id);if(route.isEmpty()||route.size()>256)return;
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(n.point.x,n.point.y,n.point.z,48,level.dimension())),
                new CurveFlowPayload(level.dimension().location(),n.id,route,item,fluid==null?FluidStack.EMPTY:fluid));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        CurveData d=CurveData.get(level);long revision=d.revision();d.tick();
        if(level.getGameTime()%20==0||revision!=d.revision())for(ServerPlayer p:level.players())sync(p);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
}
