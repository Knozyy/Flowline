package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.curved.CurveEdits;
import com.knozyy.flowline.curved.EditResult;
import com.knozyy.flowline.curved.PlaceRequest;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Curved pipe edits on the server, by a real (mock) server player: placing, breaking, moving, ends. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveEditGameTests {
    static final String TEMPLATE = CurveNetworkGameTests.TEMPLATE;
    static final BlockPos CHEST = new BlockPos(0, 1, 4), PIPE = new BlockPos(7, 1, 4);
    /** A chest's shape stops 1/16 short of its cell: its east face is at x = 15/16. */
    static final double CHEST_FACE_X = 15 / 16.0;

    /** A survival player standing at (4, 1, 7) of the test, {@code pipes} item pipes in the off hand. */
    static ServerPlayer player(GameTestHelper h, int pipes) {
        // a Forge fake player: GameTestHelper's mock server player logs in through a connection without a channel,
        // which Forge's login packets cannot use
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curve-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 7.5));
        player.moveTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.ITEM_PIPE.get(), pipes));
        return player;
    }

    static int pipesHeld(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.ITEM_PIPE.get());
    }

    static PlaceRequest.End onFace(GameTestHelper h, Vec3 rel, BlockPos block, Direction face) {
        return new PlaceRequest.End(h.absoluteVec(rel), OptionalLong.empty(),
                Optional.of(new PlaceRequest.Face(h.absolutePos(block), face)));
    }

    static PlaceRequest.End free(GameTestHelper h, Vec3 rel) {
        return new PlaceRequest.End(h.absoluteVec(rel), OptionalLong.empty(), Optional.empty());
    }

    /** Chest at (0,1,4), item pipe at (7,1,4); a request from the chest's east face to the pipe's west face. */
    static PlaceRequest chestToPipe(GameTestHelper h) {
        h.setBlock(CHEST, Blocks.CHEST);
        h.setBlock(PIPE, ModBlocks.ITEM_PIPE.get());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(PIPE));
        return new PlaceRequest(PipeType.ITEM, onFace(h, new Vec3(CHEST_FACE_X, 1.5, 4.5), CHEST, Direction.EAST),
                onFace(h, new Vec3(7, 1.5, 4.5), PIPE, Direction.WEST), List.of());
    }

    static long edges(GameTestHelper h) {
        CurveData data = CurveData.get(h.getLevel());
        CurveTestSupport.trackArea(h);
        return data.graph().edges().stream().filter(e -> inArea(h, data, e)).count();
    }

    private static boolean inArea(GameTestHelper h, CurveData data, CurveEdge edge) {
        Vec3d p = data.graph().node(edge.from()).orElseThrow().pos();
        Vec3 rel = h.relativeVec(new Vec3(p.x(), p.y(), p.z()));
        return rel.x >= 0 && rel.x <= 9 && rel.y >= 0 && rel.y <= 6 && rel.z >= 0 && rel.z <= 9;
    }

    private static void done(GameTestHelper h, ServerPlayer player) {
        CurveTestSupport.trackArea(h);
        CurveTestSupport.clear(h);
        TestProtection.allow(player);
        h.succeed();
    }

    // ---- placing ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeChargesAndBuilds(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        EditResult result = CurveEdits.place(player, chestToPipe(h));
        h.assertTrue(result.ok(), "placing works, got " + result);
        h.assertTrue(pipesHeld(player) == 9, "a 6 1/16 block curve costs 7 pipes, left " + pipesHeld(player));
        h.assertTrue(edges(h) == 1, "one curved edge");
        h.assertTrue(h.getBlockState(PIPE).getValue(PipeBlock.prop(Direction.WEST)) == Conn.PIPE, "the pipe face connects");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeWithoutEnoughPipesTakesNothing(GameTestHelper h) {
        ServerPlayer player = player(h, 1);
        EditResult result = CurveEdits.place(player, chestToPipe(h));
        h.assertTrue(result.error() == PlaceError.TOO_COSTLY && result.missing() == 6, "6 pipes short, got " + result);
        h.assertTrue(pipesHeld(player) == 1 && edges(h) == 0, "nothing is taken or built");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeTooFarIsRefused(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        Vec3 near = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.moveTo(near.x, near.y, near.z);
        PlaceRequest far = new PlaceRequest(PipeType.ITEM, free(h, new Vec3(1.5, 1.5, 1.5)), free(h, new Vec3(8.5, 1.5, 8.5)),
                List.of());
        h.assertTrue(CurveEdits.place(player, far).error() == PlaceError.TOO_FAR, "the far end is out of reach");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeThroughBlockIsRefused(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        PlaceRequest request = chestToPipe(h);
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        h.assertTrue(CurveEdits.place(player, request).error() == PlaceError.INTERSECT_BLOCK, "stone in the way");
        h.assertTrue(pipesHeld(player) == 16 && edges(h) == 0, "nothing is taken or built");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeOnOtherPipeTypeIsRefused(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        PlaceRequest request = chestToPipe(h);
        h.setBlock(PIPE, ModBlocks.FLUID_PIPE.get());
        h.assertTrue(CurveEdits.place(player, request).error() == PlaceError.INVALID_OBJECT, "an item curve on a fluid pipe");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void deniedPlacementTakesNothing(GameTestHelper h) {
        ServerPlayer player = player(h, 1);
        TestProtection.deny(player);
        EditResult result = CurveEdits.place(player, chestToPipe(h));
        h.assertTrue(result.denied(), "a protected area refuses before the cost is counted, got " + result);
        h.assertTrue(pipesHeld(player) == 1 && edges(h) == 0, "nothing is taken or built");
        done(h, player);
    }

    // ---- breaking and removing ----------------------------------------------------------------------------

    /** The curved node of the test standing at {@code rel}. */
    static CurveNode nodeAt(GameTestHelper h, Vec3 rel) {
        Vec3 abs = h.absoluteVec(rel);
        return CurveData.get(h.getLevel()).graph().nodes().stream()
                .filter(n -> n.pos().distanceSq(new Vec3d(abs.x, abs.y, abs.z)) < 1e-12).findFirst().orElseThrow();
    }

    static long onlyEdge(GameTestHelper h, Vec3 relNode) {
        return CurveData.get(h.getLevel()).graph().edgesAt(nodeAt(h, relNode).id()).get(0).id();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void breakRefundsTheCost(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        EditResult result = CurveEdits.breakEdge(player, onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5)));
        h.assertTrue(result.ok(), "broken, got " + result);
        h.assertTrue(pipesHeld(player) == 16, "every pipe comes back, held " + pipesHeld(player));
        h.assertTrue(edges(h) == 0 && CurveData.get(h.getLevel()).graph().nodes().stream()
                .noneMatch(n -> n.pos().distanceSq(new Vec3d(h.absoluteVec(new Vec3(7, 1.5, 4.5)).x,
                        h.absoluteVec(new Vec3(7, 1.5, 4.5)).y, h.absoluteVec(new Vec3(7, 1.5, 4.5)).z)) < 1e-12),
                "the edge and its lonely nodes are gone");
        h.assertTrue(h.getBlockState(PIPE).getValue(PipeBlock.prop(Direction.WEST)) == Conn.NONE, "the pipe face lets go");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void breakDropsEndUpgrades(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        CurveNode chestEnd = nodeAt(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        var end = CurveData.get(h.getLevel()).end(chestEnd.id()).orElseThrow();
        end.cfg().mode = com.knozyy.flowline.pipe.SideMode.EXTRACT;
        end.upgrades().setItem(0, new ItemStack(ModItems.SPEED_UPGRADE.get()));
        end.upgrades().setItem(1, new ItemStack(ModItems.STACK_UPGRADE.get()));
        h.assertTrue(CurveEdits.breakEdge(player, onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5))).ok(), "broken");
        Vec3d p = chestEnd.pos();
        long upgrades = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(p.x() - 1, p.y() - 1, p.z() - 1, p.x() + 1, p.y() + 1, p.z() + 1)).stream()
                .filter(e -> com.knozyy.flowline.item.UpgradeItem.typeOf(e.getItem()) != null).count();
        h.assertTrue(upgrades == 2, "the end's two upgrades drop where it was, found " + upgrades);
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void creativeBreakGivesNothing(GameTestHelper h) {
        ServerPlayer player = player(h, 1);
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "creative places for free");
        h.assertTrue(pipesHeld(player) == 1, "nothing taken");
        h.assertTrue(CurveEdits.breakEdge(player, onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5))).ok(), "broken");
        h.assertTrue(pipesHeld(player) == 1, "and nothing given back");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void removeMiddleNodeMergesEdges(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        PlaceRequest straight = chestToPipe(h);
        Vec3 bend = new Vec3(4, 2.5, 4.5);
        EditResult placed = CurveEdits.place(player, new PlaceRequest(PipeType.ITEM, straight.start(), straight.end(),
                List.of(h.absoluteVec(bend))));
        h.assertTrue(placed.ok(), "a bent chain through a middle node, got " + placed);
        CurveData data = CurveData.get(h.getLevel());
        CurveNode middle = nodeAt(h, bend);
        List<CurveEdge> before = data.graph().edgesAt(middle.id());
        int oldCost = before.get(0).shape().cost() + before.get(1).shape().cost();
        int held = pipesHeld(player);
        EditResult removed = CurveEdits.removeNode(player, middle.id());
        h.assertTrue(removed.ok(), "removing the middle node, got " + removed);
        CurveNode chestEnd = nodeAt(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        List<CurveEdge> after = data.graph().edgesAt(chestEnd.id());
        h.assertTrue(after.size() == 1 && data.graph().node(middle.id()).isEmpty(), "one edge, no middle node");
        CurveNode pipeEnd = nodeAt(h, new Vec3(7, 1.5, 4.5));
        CurveEdge merged = after.get(0);
        h.assertTrue(merged.from() == chestEnd.id() && merged.to() == pipeEnd.id(), "it joins the same two ends");
        h.assertTrue(pipesHeld(player) == held + oldCost - merged.shape().cost(), "the cost difference is settled");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void removeJointIsRefused(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long joint = CurveTestSupport.node(h, new Vec3(4.5, 2.5, 4.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        for (Vec3 far : List.of(new Vec3(2.5, 2.5, 4.5), new Vec3(6.5, 2.5, 4.5), new Vec3(4.5, 2.5, 6.5))) {
            long other = CurveTestSupport.node(h, far, com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
            CurveTestSupport.edge(h, joint, other, PipeType.ITEM, com.knozyy.flowline.curve.EndCondition.FREE,
                    com.knozyy.flowline.curve.EndCondition.FREE);
        }
        h.assertTrue(CurveEdits.removeNode(player, joint).error() == PlaceError.INVALID_OBJECT, "a joint cannot be removed");
        done(h, player);
    }

    // ---- inserting, moving, forcing ------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void insertSplitsAnEdge(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        CurveData data = CurveData.get(h.getLevel());
        long edgeId = onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        CurveEdge edge = data.graph().edge(edgeId).orElseThrow();
        Vec3d a = edge.shape().points().get(4), b = edge.shape().points().get(5);
        Vec3d middle = a.add(b).scale(0.5);
        int held = pipesHeld(player), oldCost = edge.shape().cost();
        EditResult result = CurveEdits.insert(player, edgeId, 4, 0.5);
        h.assertTrue(result.ok(), "inserting, got " + result);
        h.assertTrue(data.graph().edge(edgeId).isEmpty(), "the old edge is replaced");
        CurveNode added = data.graph().nodes().stream().filter(n -> data.graph().edgesAt(n.id()).size() == 2
                && n.pos().distanceSq(middle) < 0.04).findFirst().orElse(null);
        h.assertTrue(added != null, "a node with two edges near the middle of segment 4");
        int newCost = data.graph().edgesAt(added.id()).stream().mapToInt(e -> e.shape().cost()).sum();
        h.assertTrue(pipesHeld(player) == held - (newCost - oldCost), "the cost difference is settled");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void insertOutsideTheEdgeIsRefused(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        long edgeId = onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        h.assertTrue(CurveEdits.insert(player, edgeId, 4, 1.5).error() == PlaceError.INVALID_OBJECT, "beyond the segment");
        h.assertTrue(CurveEdits.insert(player, edgeId, 999, 0.5).error() == PlaceError.INVALID_OBJECT, "no such segment");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void translateReshapesTouchingEdges(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        h.setBlock(CHEST, Blocks.CHEST);
        Vec3 loose = new Vec3(4, 2, 4.5), moved = new Vec3(4, 3, 4.5);
        h.assertTrue(CurveEdits.place(player, new PlaceRequest(PipeType.ITEM,
                onFace(h, new Vec3(CHEST_FACE_X, 1.5, 4.5), CHEST, Direction.EAST), free(h, loose), List.of())).ok(), "placed");
        CurveNode end = nodeAt(h, loose);
        EditResult result = CurveEdits.translate(player, end.id(), h.absoluteVec(moved), Optional.empty());
        h.assertTrue(result.ok(), "moving the loose end, got " + result);
        CurveData data = CurveData.get(h.getLevel());
        Vec3 abs = h.absoluteVec(moved);
        Vec3d target = new Vec3d(abs.x, abs.y, abs.z);
        h.assertTrue(data.graph().node(end.id()).orElseThrow().pos().distanceSq(target) < 1e-12, "the node moved");
        List<Vec3d> points = data.graph().edgesAt(end.id()).get(0).shape().points();
        h.assertTrue(points.get(points.size() - 1).distanceSq(target) < 1e-12, "the edge follows it");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void translateOffTheFaceDetaches(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        CurveData data = CurveData.get(h.getLevel());
        CurveNode chestEnd = nodeAt(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        var end = data.end(chestEnd.id()).orElseThrow();
        end.cfg().mode = com.knozyy.flowline.pipe.SideMode.EXTRACT;
        end.upgrades().setItem(0, new ItemStack(ModItems.SPEED_UPGRADE.get()));
        Vec3 air = h.absoluteVec(new Vec3(2.5, 2.5, 4.5));
        EditResult result = CurveEdits.translate(player, chestEnd.id(), air, Optional.empty());
        h.assertTrue(result.ok(), "moving the end into the air, got " + result);
        h.assertTrue(data.anchor(chestEnd.id()).equals(com.knozyy.flowline.curved.Anchor.FREE)
                && data.end(chestEnd.id()).isEmpty(), "the end leaves the chest and its settings go");
        long upgrades = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, 0, 3))).inflate(2)).stream()
                .filter(e -> com.knozyy.flowline.item.UpgradeItem.typeOf(e.getItem()) != null).count();
        h.assertTrue(upgrades == 1, "its upgrade drops at the chest, found " + upgrades);
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void forceJointGrowsTheJoint(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        CurveData data = CurveData.get(h.getLevel());
        long m = CurveTestSupport.node(h, new Vec3(4.5, 2.5, 4.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        long far = CurveTestSupport.node(h, new Vec3(1.5, 2.5, 4.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        long other = CurveTestSupport.node(h, new Vec3(4.5, 2.5, 7.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        CurveTestSupport.edge(h, far, m, PipeType.ITEM, com.knozyy.flowline.curve.EndCondition.FREE,
                com.knozyy.flowline.curve.EndCondition.FREE);
        CurveTestSupport.edge(h, m, other, PipeType.ITEM, com.knozyy.flowline.curve.EndCondition.FREE,
                com.knozyy.flowline.curve.EndCondition.FREE);
        h.assertTrue(CurveEdits.forceJoint(player, m, true).ok() && data.graph().node(m).orElseThrow().forceJoint(),
                "a node with room around it becomes a forced joint");
        // a stub whose far end lies 0.22 away: outside 0.1875, inside the forced 0.1875·√2 ≈ 0.265
        long close = CurveTestSupport.node(h, new Vec3(2.5, 4.5, 6.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        long stub = CurveTestSupport.node(h, new Vec3(2.72, 4.5, 6.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        long beyond = CurveTestSupport.node(h, new Vec3(2.5, 4.5, 3.5), com.knozyy.flowline.curved.Anchor.FREE, PipeType.ITEM);
        CurveTestSupport.edge(h, close, stub, PipeType.ITEM, com.knozyy.flowline.curve.EndCondition.FREE,
                com.knozyy.flowline.curve.EndCondition.FREE);
        CurveTestSupport.edge(h, close, beyond, PipeType.ITEM, com.knozyy.flowline.curve.EndCondition.FREE,
                com.knozyy.flowline.curve.EndCondition.FREE);
        EditResult forced = CurveEdits.forceJoint(player, close, true);
        h.assertTrue(forced.error() == PlaceError.NODE_IN_JOINT, "forcing would swallow the stub's far end, got " + forced);
        h.assertTrue(!data.graph().node(close).orElseThrow().forceJoint(), "and the flag stays off");
        done(h, player);
    }

    // ---- curved ends: screen, wrench, Filter Card ----------------------------------------------------------

    /** Chest at (0,1,4), item pipe at (7,1,4), a placed curve between them; returns the chest end's node id. */
    static long placedEnd(GameTestHelper h, ServerPlayer player) {
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        return nodeAt(h, new Vec3(CHEST_FACE_X, 1.5, 4.5)).id();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void curvedEndOpensTheSameScreen(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long node = placedEnd(h, player);
        var end = CurveData.get(h.getLevel()).end(node).orElseThrow();
        EditResult opened = CurveEdits.openEnd(player, node);
        h.assertTrue(opened.ok(), "the end's screen opens, got " + opened);
        h.assertTrue(player.containerMenu instanceof com.knozyy.flowline.menu.PipeMenu, "it is the pipe side screen");
        var before = end.cfg().distribution;
        player.containerMenu.clickMenuButton(player, com.knozyy.flowline.menu.PipeMenu.BTN_DISTRIBUTION);
        h.assertTrue(end.cfg().distribution != before, "and its buttons change the end's settings");
        player.closeContainer();
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void curvedEndAcceptsUpgradesLikeASide(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long node = placedEnd(h, player);
        var end = CurveData.get(h.getLevel()).end(node).orElseThrow();
        end.cfg().mode = com.knozyy.flowline.pipe.SideMode.EXTRACT;
        h.assertTrue(CurveEdits.openEnd(player, node).ok(), "opened");
        var slot = player.containerMenu.slots.get(0);
        h.assertTrue(slot.mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get())), "an extracting end takes a speed upgrade");
        slot.set(new ItemStack(ModItems.SPEED_UPGRADE.get()));
        h.assertTrue(end.cfg().speedCount == 1, "and counts it");
        player.closeContainer();
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void cycleEndTogglesExtract(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long node = placedEnd(h, player);
        var end = CurveData.get(h.getLevel()).end(node).orElseThrow();
        h.assertTrue(end.cfg().mode == com.knozyy.flowline.pipe.SideMode.INSERT, "a new end inserts");
        h.assertTrue(CurveEdits.cycleEnd(player, node).ok() && end.cfg().mode == com.knozyy.flowline.pipe.SideMode.EXTRACT,
                "the wrench makes it extract");
        h.assertTrue(CurveEdits.cycleEnd(player, node).ok() && end.cfg().mode == com.knozyy.flowline.pipe.SideMode.INSERT,
                "and insert again");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void curvedEndMenuClosesWhenEndGoes(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long node = placedEnd(h, player);
        h.assertTrue(CurveEdits.openEnd(player, node).ok(), "opened");
        var menu = player.containerMenu;
        h.assertTrue(menu.stillValid(player), "valid while the end is there");
        h.assertTrue(CurveEdits.breakEdge(player, onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5))).ok(), "broken");
        h.assertTrue(!menu.stillValid(player), "the screen of a removed end closes");
        player.closeContainer();
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void filterCardWorksOnCurvedEnds(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        long node = placedEnd(h, player);
        BlockPos source = new BlockPos(7, 1, 3), chest = new BlockPos(7, 1, 2);
        h.setBlock(source, ModBlocks.ITEM_PIPE.get());
        h.setBlock(chest, Blocks.CHEST);
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(source));
        var pipe = CurveNetworkGameTests.pipe(h, source);
        pipe.side(Direction.NORTH).setEntry(0, com.knozyy.flowline.filter.FilterEntry.ofItem("minecraft:dirt"));
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        player.setShiftKeyDown(true);
        ModItems.FILTER_CARD.get().useOnPipe(pipe, Direction.NORTH,
                h.getBlockState(source).getValue(PipeBlock.prop(Direction.NORTH)), player, InteractionHand.MAIN_HAND, card);
        player.setShiftKeyDown(false);
        EditResult pasted = CurveEdits.useCardOnEnd(player, node, InteractionHand.MAIN_HAND);
        h.assertTrue(pasted.ok(), "the card pastes onto the curved end, got " + pasted);
        var rule = CurveData.get(h.getLevel()).end(node).orElseThrow().cfg().getEntry(0);
        h.assertTrue(rule != null && rule.sameMatch(com.knozyy.flowline.filter.FilterEntry.ofItem("minecraft:dirt")),
                "with the same rule");
        done(h, player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeExtendsAnEnd(GameTestHelper h) {
        ServerPlayer player = player(h, 64);
        h.setBlock(CHEST, Blocks.CHEST);
        h.setBlock(PIPE, ModBlocks.ITEM_PIPE.get());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(PIPE));
        Vec3 middle = new Vec3(4, 2, 4.5);
        EditResult first = CurveEdits.place(player, new PlaceRequest(PipeType.ITEM,
                onFace(h, new Vec3(CHEST_FACE_X, 1.5, 4.5), CHEST, Direction.EAST), free(h, middle), List.of()));
        h.assertTrue(first.ok(), "the first piece, got " + first);
        CurveData data = CurveData.get(h.getLevel());
        Vec3 abs = h.absoluteVec(middle);
        CurveNode end = data.graph().nodes().stream()
                .filter(n -> n.pos().distanceSq(new Vec3d(abs.x, abs.y, abs.z)) < 1e-12).findFirst().orElseThrow();
        CurveEdge old = data.graph().edgesAt(end.id()).get(0);
        EditResult second = CurveEdits.place(player, new PlaceRequest(PipeType.ITEM,
                new PlaceRequest.End(abs, OptionalLong.of(end.id()), Optional.empty()),
                onFace(h, new Vec3(7, 1.5, 4.5), PIPE, Direction.WEST), List.of()));
        h.assertTrue(second.ok(), "continuing from the free end, got " + second);
        CurveEdge added = data.graph().edgesAt(end.id()).stream().filter(e -> e.id() != old.id()).findFirst().orElseThrow();
        Vec3d arriving = com.knozyy.flowline.curve.CurveGraph.exitDirection(old, end.id()).scale(-1);
        Vec3d leaving = com.knozyy.flowline.curve.CurveGraph.exitDirection(added, end.id());
        h.assertTrue(arriving.dot(leaving) > 1 - 1e-9, "the new piece carries straight on, dot " + arriving.dot(leaving));
        done(h, player);
    }

    // ---- painting -----------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintingAnEdgeSplitsTheNetwork(GameTestHelper h) {
        ServerPlayer player = player(h, 16);
        h.assertTrue(CurveEdits.place(player, chestToPipe(h)).ok(), "placed");
        ((PipeBlockEntity) h.getBlockEntity(PIPE)).setColor(DyeColor.WHITE.getId());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(PIPE));
        h.assertTrue(h.getBlockState(PIPE).getValue(PipeBlock.prop(Direction.WEST)) == Conn.PIPE, "an undyed edge fits a white pipe");
        long edge = onlyEdge(h, new Vec3(CHEST_FACE_X, 1.5, 4.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 2));
        EditResult red = CurveEdits.paint(player, edge, InteractionHand.MAIN_HAND);
        h.assertTrue(red.ok(), "dyed, got " + red);
        h.assertTrue(CurveData.get(h.getLevel()).info(edge).color() == DyeColor.RED.getId(), "the edge is red");
        h.assertTrue(h.getBlockState(PIPE).getValue(PipeBlock.prop(Direction.WEST)) == Conn.NONE, "red and white let go");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "one dye is used");
        h.assertTrue(CurveEdits.paint(player, edge, InteractionHand.MAIN_HAND).ok(), "dyed again");
        h.assertTrue(CurveData.get(h.getLevel()).info(edge).color() == PipeBlockEntity.NO_COLOR, "the same dye washes it off");
        h.assertTrue(h.getBlockState(PIPE).getValue(PipeBlock.prop(Direction.WEST)) == Conn.PIPE, "and the face joins again");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "washing costs nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(CurveEdits.paint(player, edge, InteractionHand.MAIN_HAND).error() == PlaceError.INVALID_OBJECT, "no dye");
        done(h, player);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curvePaintPayloadRoundTrips(GameTestHelper h) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var payload = new com.knozyy.flowline.network.curve.CurvePaintPayload(7, 1L << 40, InteractionHand.OFF_HAND);
            payload.encode(buf);
            h.assertTrue(com.knozyy.flowline.network.curve.CurvePaintPayload.decode(buf).equals(payload), "round trip");
        } finally {
            buf.release();
        }
        h.succeed();
    }
}
