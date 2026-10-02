package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.*;
import com.knozyy.flowline.network.*;
import com.knozyy.flowline.pipe.*;
import com.knozyy.flowline.registry.ModItems;
import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import static com.knozyy.flowline.network.CurveActionPayload.Action;

@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveGameTests {
    private static Vec3 point(GameTestHelper h,double x,double y,double z){return Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)).add(x,y,z);}
    private static CurveNode free(CurveData d,UUID owner,Vec3 p){return d.addNode(PipeType.ITEM,owner,p,null,Direction.UP);}
    private static ServerPlayer player(GameTestHelper h){
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"flowline-curve-test"));
        p.getAbilities().mayBuild=true;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.ITEM_PIPE.get(),16));return p;
    }
    private static void look(ServerPlayer p,Vec3 eye,Vec3 target){
        p.setPos(eye.x,eye.y-p.getEyeHeight(),eye.z);Vec3 d=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));p.setYRot(yaw);p.setYHeadRot(yaw);
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
        p.tickCount+=3;
    }
    private static CurveActionPayload add(Vec3 p){return new CurveActionPayload(Action.ADD,0,p,null,Direction.UP,false);}
    private static void cleanup(CurveData d,UUID owner){for(var n:List.copyOf(d.nodes().values()))if(n.owner.equals(owner))d.removeNode(n.id);}

    @GameTest(template="empty",timeoutTicks=20)
    public static void curvesInOneBlockRemainIndependent(GameTestHelper h){
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();
        var a=free(d,owner,point(h,1.2,1.35,1.25));var b=free(d,owner,point(h,1.8,1.35,1.25));
        var c=free(d,owner,point(h,1.2,1.75,1.75));var e=free(d,owner,point(h,1.8,1.75,1.75));
        d.connect(a.id,b.id,1);d.connect(c.id,e.id,1);
        h.assertTrue(d.component(a.id).equals(Set.of(a.id,b.id)),"sharing a block must not connect separate curves");
        var branch=free(d,owner,point(h,2.4,1.8,1.5));var edge=d.connect(b.id,branch.id,1);
        h.assertTrue(d.component(a.id).contains(branch.id),"an explicit branch connects");
        d.removeEdge(edge.id());h.assertTrue(!d.component(a.id).contains(branch.id),"cutting an edge splits the graph");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvesPersistEndpointFiltersUpgradesAndShape(GameTestHelper h){
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();BlockPos chest=h.absolutePos(new BlockPos(0,1,1));
        var a=d.addNode(PipeType.ITEM,owner,point(h,1.1,1.5,1.5),chest,Direction.EAST);
        var b=free(d,owner,point(h,2.5,1.8,1.5));b.joint=true;var edge=d.connect(a.id,b.id,2);
        a.config.side(a.side()).mode=SideMode.EXTRACT;a.config.side(a.side()).priority=23;
        a.config.side(a.side()).setEntry(0,FilterEntry.ofName("diamond"));
        a.config.installUpgrade(a.side(),new ItemStack(ModItems.SPEED_UPGRADE.get()));
        var saved=d.save(new CompoundTag());var loaded=CurveData.load(h.getLevel(),saved);var n=loaded.nodes().get(a.id);
        h.assertTrue(loaded.component(a.id).equals(d.component(a.id)),"graph identities and edges survive loading");
        h.assertTrue(n.config.side(n.side()).mode==SideMode.EXTRACT&&n.config.side(n.side()).priority==23,"endpoint settings survive");
        h.assertTrue(n.config.getUpgrade(n.side(),0).is(ModItems.SPEED_UPGRADE.get())&&n.config.side(n.side()).hasRules(),"upgrades and filter rules survive");
        h.assertTrue(loaded.nodes().get(b.id).joint&&loaded.shape(loaded.edges().get(edge.id())).equals(d.shape(edge)),"shape survives exactly");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvesRejectBlockedAndIntersectingRoutes(GameTestHelper h){
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();
        var a=free(d,owner,point(h,1.2,1.5,1.5));var b=free(d,owner,point(h,4.8,1.5,1.5));d.connect(a.id,b.id,4);
        h.assertTrue(CurveServer.valid(d,d.component(a.id)),"open route is valid");
        h.setBlock(new BlockPos(3,1,1),Blocks.STONE);h.assertTrue(!CurveServer.valid(d,d.component(a.id)),"solid block blocks the physical curve");
        h.setBlock(new BlockPos(3,1,1),Blocks.AIR);
        var c=free(d,owner,point(h,3,1.5,0.25));var e=free(d,owner,point(h,3,1.5,2.75));d.connect(c.id,e.id,3);
        h.assertTrue(!CurveServer.valid(d,d.component(c.id)),"independent crossing tubes are rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curveCollisionParticipatesInVanillaQueries(GameTestHelper h){
        CurveData d=CurveData.get(h.getLevel());UUID owner=UUID.randomUUID();
        try{
            var a=free(d,owner,point(h,1.2,1.5,1.5));var b=free(d,owner,point(h,4.8,1.5,1.5));d.connect(a.id,b.id,4);
            Vec3 p=point(h,3,1.5,1.5);
            h.assertTrue(!h.getLevel().noCollision(new AABB(p,p).inflate(0.06)),"real vanilla collision queries must include curves; checks mixin activation");
            AABB body=new AABB(p.add(0,0.5,0),p.add(0,0.5,0)).inflate(0.05);
            var collisions=h.getLevel().getBlockCollisions(null,body.expandTowards(0,-1,0));
            double movement=net.minecraft.world.phys.shapes.Shapes.collide(Direction.Axis.Y,body,collisions,-1);
            h.assertTrue(movement>-0.5&&movement<0,"world-space curve collision must stop actual downward movement");
            h.assertTrue(h.getLevel().noCollision(new AABB(p.add(0,0.5,0),p.add(0,0.5,0)).inflate(0.06)),"nearby empty volume remains free");
        }finally{cleanup(d,owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvePlacementPaysOnlyAfterValidation(GameTestHelper h){
        ServerPlayer p=player(h);CurveData d=CurveData.get(h.getLevel());UUID owner=p.getUUID();
        try{
            Vec3 first=point(h,2,1.5,1.5),second=point(h,4,1.5,1.5),eye=point(h,3,1.5,0.3);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.ITEM_PIPE.get(),1));
            look(p,eye,first);CurveServer.handle(p,add(first));
            h.assertTrue(d.nodes().values().stream().filter(n->n.owner.equals(owner)).count()==1,"first point establishes a draft");
            look(p,eye,second);CurveServer.handle(p,add(second));
            h.assertTrue(d.edges().values().stream().noneMatch(e->d.nodes().get(e.a()).owner.equals(owner)),"insufficient materials must not create a partial edge");
            h.assertTrue(p.getMainHandItem().getCount()==1,"failed validation must not consume pipes");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.ITEM_PIPE.get(),16));
            look(p,eye,second);CurveServer.handle(p,add(second));
            var edges=d.edges().values().stream().filter(e->d.nodes().get(e.a()).owner.equals(owner)).toList();
            h.assertTrue(edges.size()==1&&edges.get(0).paid()==2&&p.getMainHandItem().getCount()==14,"a two-block curve costs two pipes");
        }finally{CurveServer.handle(p,CurveActionPayload.simple(Action.CANCEL,0));cleanup(d,owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curveActionsRejectForgedPositionAndWrongTool(GameTestHelper h){
        ServerPlayer p=player(h);CurveData d=CurveData.get(h.getLevel());UUID owner=p.getUUID();
        try{
            Vec3 eye=point(h,1,1.5,1.5),target=point(h,3,1.5,1.5);look(p,eye,target);
            CurveServer.handle(p,add(target.add(100,0,0)));
            h.assertTrue(d.nodes().values().stream().noneMatch(n->n.owner.equals(owner)),"out-of-range placement rejected");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));look(p,eye,target);CurveServer.handle(p,add(target));
            h.assertTrue(d.nodes().values().stream().noneMatch(n->n.owner.equals(owner)),"non-pipe cannot place curves");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.ITEM_PIPE.get(),16));h.setBlock(new BlockPos(2,1,1),Blocks.STONE);
            look(p,eye,target);CurveServer.handle(p,add(target));
            h.assertTrue(d.nodes().values().stream().noneMatch(n->n.owner.equals(owner)),"placement behind a solid wall rejected");
        }finally{CurveServer.handle(p,CurveActionPayload.simple(Action.CANCEL,0));cleanup(d,owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void machineClickStartsCurveAndSecondMachineFinishesIt(GameTestHelper h){
        ServerPlayer p=player(h);CurveData d=CurveData.get(h.getLevel());UUID owner=p.getUUID();
        BlockPos left=h.absolutePos(new BlockPos(0,1,1)),right=h.absolutePos(new BlockPos(5,1,1));
        h.setBlock(new BlockPos(0,1,1),Blocks.CHEST);h.setBlock(new BlockPos(5,1,1),Blocks.CHEST);
        try{
            Vec3 first=point(h,1.1,1.5,1.5),second=point(h,4.9,1.5,1.5),eye=point(h,3,1.5,1.5);
            look(p,eye,first);CurveServer.handle(p,new CurveActionPayload(Action.ADD,0,first,left,Direction.EAST,true));
            var nodes=d.nodes().values().stream().filter(n->n.owner.equals(owner)).toList();
            h.assertTrue(nodes.size()==1&&nodes.get(0).block.equals(left),"a first machine click must keep its draft endpoint");
            h.assertTrue(nodes.get(0).config.side(nodes.get(0).side()).mode==SideMode.EXTRACT,"first machine endpoint extracts");
            look(p,eye,second);CurveServer.handle(p,new CurveActionPayload(Action.ADD,0,second,right,Direction.WEST,true));
            var edges=d.edges().values().stream().filter(e->d.nodes().get(e.a()).owner.equals(owner)).toList();
            h.assertTrue(edges.size()==1&&edges.get(0).paid()==4,"second machine completes a paid connection from the first");
            nodes=d.nodes().values().stream().filter(n->n.owner.equals(owner)).toList();
            h.assertTrue(nodes.size()==2&&nodes.stream().filter(n->n.block.equals(right)).allMatch(n->n.config.side(n.side()).mode==SideMode.INSERT),"destination machine inserts");
            Vec3 third=point(h,3,2.5,1.5);look(p,eye,third);CurveServer.handle(p,add(third));
            h.assertTrue(d.edges().values().stream().filter(e->d.nodes().get(e.a()).owner.equals(owner)).count()==1,"finished machine connection must not remain the next draft's cursor");
        }finally{CurveServer.handle(p,CurveActionPayload.simple(Action.CANCEL,0));cleanup(d,owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void reattachingCurveRotatesSettingsAndUpgradeSlots(GameTestHelper h){
        ServerPlayer p=player(h);CurveData d=CurveData.get(h.getLevel());UUID owner=p.getUUID();
        BlockPos old=h.absolutePos(new BlockPos(0,1,1)),dest=h.absolutePos(new BlockPos(5,1,1));
        h.setBlock(new BlockPos(0,1,1),Blocks.CHEST);h.setBlock(new BlockPos(5,1,1),Blocks.CHEST);
        try{
            var a=d.addNode(PipeType.ITEM,owner,point(h,1.1,1.5,1.5),old,Direction.EAST);
            var b=free(d,owner,point(h,3.5,1.5,1.5));d.connect(a.id,b.id,3);
            a.config.side(a.side()).mode=SideMode.EXTRACT;a.config.side(a.side()).priority=17;
            a.config.side(a.side()).setEntry(0,FilterEntry.ofName("diamond"));a.config.installUpgrade(a.side(),new ItemStack(ModItems.SPEED_UPGRADE.get()));
            Vec3 next=point(h,4.9,1.5,1.5);look(p,point(h,3,1.5,1.5),next);
            CurveServer.handle(p,new CurveActionPayload(Action.MOVE,a.id,next,dest,Direction.WEST,true));
            h.assertTrue(a.block.equals(dest)&&a.face==Direction.WEST,"endpoint actually reattaches to the opposite face");
            h.assertTrue(a.config.side(a.side()).mode==SideMode.EXTRACT&&a.config.side(a.side()).priority==17&&a.config.side(a.side()).hasRules(),"mode, filter and priority move to the new face");
            h.assertTrue(a.config.getUpgrade(a.side(),0).is(ModItems.SPEED_UPGRADE.get())&&a.config.getUpgrade(Direction.WEST,0).isEmpty(),"upgrades rotate once, without duplication");
        }finally{cleanup(d,owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void creativeCurvesRecordLengthWithoutCreatingRefunds(GameTestHelper h){
        ServerPlayer p=player(h);CurveData d=CurveData.get(h.getLevel());UUID owner=p.getUUID();
        p.getAbilities().instabuild=true;
        try{
            Vec3 first=point(h,2,1.5,1.5),second=point(h,4,1.5,1.5),eye=point(h,3,1.5,0.3);
            look(p,eye,first);CurveServer.handle(p,add(first));look(p,eye,second);CurveServer.handle(p,add(second));
            var edges=d.edges().values().stream().filter(e->d.nodes().get(e.a()).owner.equals(owner)).toList();
            h.assertTrue(edges.size()==1&&edges.get(0).units()==2&&edges.get(0).paid()==0,"creative curves record their geometry cost but no refundable survival materials");
            h.assertTrue(p.getMainHandItem().getCount()==16,"creative placement consumes no stack");
            var loaded=CurveData.load(h.getLevel(),d.save(new CompoundTag()));
            h.assertTrue(loaded.edges().get(edges.get(0).id()).paid()==0&&loaded.edges().get(edges.get(0).id()).units()==2,"creative accounting survives reload");
        }finally{CurveServer.handle(p,CurveActionPayload.simple(Action.CANCEL,0));cleanup(d,owner);}h.succeed();
    }
    private static class ResourcesChest extends ChestBlockEntity{
        final FluidTank tank=new FluidTank(2000);final EnergyStorage energy=new EnergyStorage(10000);
        final LazyOptional<IFluidHandler> fluids=LazyOptional.of(()->tank);final LazyOptional<net.minecraftforge.energy.IEnergyStorage> fe=LazyOptional.of(()->energy);
        ResourcesChest(BlockPos pos){super(pos,Blocks.CHEST.defaultBlockState());}
        @Override public <T>LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==ForgeCapabilities.FLUID_HANDLER?fluids.cast():cap==ForgeCapabilities.ENERGY?fe.cast():super.getCapability(cap,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();fluids.invalidate();fe.invalidate();}
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvedPulseRunsOncePerRisingEdgeEvenDuringCooldown(GameTestHelper h){
        BlockPos left=new BlockPos(0,1,1),right=new BlockPos(5,1,1),power=new BlockPos(0,2,1);
        h.setBlock(left,Blocks.CHEST);h.setBlock(right,Blocks.CHEST);
        ChestBlockEntity source=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(left));
        ChestBlockEntity target=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(right));
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();
        var a=d.addNode(PipeType.ITEM,owner,point(h,1.1,1.5,1.5),source.getBlockPos(),Direction.EAST);
        var b=d.addNode(PipeType.ITEM,owner,point(h,4.9,1.5,1.5),target.getBlockPos(),Direction.WEST);
        d.connect(a.id,b.id,4);SideConfig cfg=a.config.side(a.side());
        cfg.mode=SideMode.EXTRACT;cfg.redstone=RedstoneMode.PULSE;cfg.interval=50;cfg.cooldown=50;
        source.setItem(0,new ItemStack(Items.DIAMOND,1));d.tick();
        h.assertTrue(target.countItem(Items.DIAMOND)==0,"low signal must not transfer");
        h.setBlock(power,Blocks.REDSTONE_BLOCK);d.tick();
        h.assertTrue(target.countItem(Items.DIAMOND)==1,"rising edge must run despite a pending adaptive cooldown");
        source.setItem(0,new ItemStack(Items.DIAMOND,1));d.tick();
        h.assertTrue(target.countItem(Items.DIAMOND)==1,"held signal must not repeat the pulse");
        h.setBlock(power,Blocks.AIR);d.tick();h.setBlock(power,Blocks.REDSTONE_BLOCK);d.tick();
        h.assertTrue(target.countItem(Items.DIAMOND)==2,"the next rising edge must transfer exactly once");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvedEnergyRunsEveryTickWhileWorking(GameTestHelper h){
        BlockPos left=new BlockPos(0,1,1),right=new BlockPos(5,1,1);h.setBlock(left,Blocks.CHEST);h.setBlock(right,Blocks.CHEST);
        ResourcesChest source=new ResourcesChest(h.absolutePos(left)),target=new ResourcesChest(h.absolutePos(right));
        h.getLevel().removeBlockEntity(source.getBlockPos());h.getLevel().removeBlockEntity(target.getBlockPos());
        h.getLevel().setBlockEntity(source);h.getLevel().setBlockEntity(target);
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();
        var a=d.addNode(PipeType.ENERGY,owner,point(h,1.1,1.5,1.5),source.getBlockPos(),Direction.EAST);
        var b=d.addNode(PipeType.ENERGY,owner,point(h,4.9,1.5,1.5),target.getBlockPos(),Direction.WEST);
        d.connect(a.id,b.id,4);a.config.side(a.side()).mode=SideMode.EXTRACT;
        source.energy.receiveEnergy(1000,false);d.tick();
        source.energy.receiveEnergy(1000,false);d.tick();
        h.assertTrue(target.energy.getEnergyStored()==2000,"a working curved energy pipe must not skip ticks");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvedUniversalTransfersRealResourcesAndStopsWhenCut(GameTestHelper h){
        BlockPos left=new BlockPos(0,1,1),right=new BlockPos(5,1,1);h.setBlock(left,Blocks.CHEST);h.setBlock(right,Blocks.CHEST);
        ResourcesChest source=new ResourcesChest(h.absolutePos(left)),target=new ResourcesChest(h.absolutePos(right));
        h.getLevel().removeBlockEntity(source.getBlockPos());h.getLevel().removeBlockEntity(target.getBlockPos());
        h.getLevel().setBlockEntity(source);h.getLevel().setBlockEntity(target);
        source.setItem(0,new ItemStack(Items.DIAMOND,5));source.tank.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);source.energy.receiveEnergy(1000,false);
        CurveData d=new CurveData(h.getLevel());UUID owner=UUID.randomUUID();
        var a=d.addNode(PipeType.UNIVERSAL,owner,point(h,1.1,1.5,1.5),source.getBlockPos(),Direction.EAST);
        var b=d.addNode(PipeType.UNIVERSAL,owner,point(h,4.9,1.5,1.5),target.getBlockPos(),Direction.WEST);var edge=d.connect(a.id,b.id,4);
        a.config.side(a.side()).mode=SideMode.EXTRACT;d.tick();
        h.assertTrue(source.getItem(0).isEmpty()&&target.getItem(0).getCount()==5,"item transfer is real and conserved");
        h.assertTrue(source.tank.getFluidAmount()==0&&target.tank.getFluidAmount()==1000,"fluid transfer is real and conserved");
        h.assertTrue(source.energy.getEnergyStored()==0&&target.energy.getEnergyStored()==1000,"energy transfer is real and conserved");
        d.removeEdge(edge.id());source.setItem(0,new ItemStack(Items.DIAMOND,3));a.config.side(a.side()).cooldown=0;d.tick();
        h.assertTrue(source.getItem(0).getCount()==3&&target.getItem(0).getCount()==5,"cut curve cannot keep transferring via a stale target");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void curvePacketsRoundTripAndRejectInvalidGeometry(GameTestHelper h){
        var p=new CurveViewPayload(h.getLevel().dimension().location(),1,
                List.of(new CurveViewPayload.Node(1,PipeType.ITEM,new Vec3(1,2,3),null,Direction.UP,2),new CurveViewPayload.Node(2,PipeType.ITEM,new Vec3(2,2,3),null,Direction.UP,2)),
                List.of(new CurveViewPayload.Edge(3,1,2,new Vec3(0.75,0,0),new Vec3(0.75,0,0))),false);
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{p.encode(b);h.assertTrue(CurveViewPayload.decode(b).equals(p),"curve geometry packet round trip");}finally{b.release();}
        b=new FriendlyByteBuf(Unpooled.buffer());try{
            b.writeResourceLocation(p.dimension());b.writeVarInt(0);b.writeBoolean(false);b.writeVarInt(CurveViewPayload.MAX_NODES+1);
            boolean rejected=false;try{CurveViewPayload.decode(b);}catch(DecoderException ex){rejected=true;}h.assertTrue(rejected,"oversized node list rejected");
        }finally{b.release();}
        b=new FriendlyByteBuf(Unpooled.buffer());try{
            new CurveActionPayload(Action.ADD,0,new Vec3(Double.NaN,0,0),null,Direction.UP,false).encode(b);
            boolean rejected=false;try{CurveActionPayload.decode(b);}catch(DecoderException ex){rejected=true;}h.assertTrue(rejected,"non-finite placement rejected before server work");
        }finally{b.release();}h.succeed();
    }
}
