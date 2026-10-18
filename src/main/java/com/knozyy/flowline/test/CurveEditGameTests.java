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
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
}
