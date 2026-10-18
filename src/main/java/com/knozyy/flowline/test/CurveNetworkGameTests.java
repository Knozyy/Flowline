package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Block pipe networks that run through curved pipes, and curved pipe ends that work like pipe sides. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveNetworkGameTests {
    static final String TEMPLATE = "curve";
    private static final EndCondition EAST = new EndCondition(new Vec3d(1, 0, 0));
    private static final EndCondition SOUTH = new EndCondition(new Vec3d(0, 0, 1));

    static ChestBlockEntity chest(GameTestHelper h, BlockPos pos) {
        return (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    static int count(GameTestHelper h, BlockPos pos, Item item) {
        return chest(h, pos).countItem(item);
    }

    static PipeBlockEntity pipe(GameTestHelper h, BlockPos pos) {
        return (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    static Conn conn(GameTestHelper h, BlockPos pos, Direction dir) {
        return h.getBlockState(pos).getValue(PipeBlock.prop(dir));
    }

    /**
     * Chest A at (0,1,z), an extracting item pipe at (1,1,z), and a straight curved edge from the pipe's east face to
     * chest B's west face at (7,1,z). Returns the edge id.
     */
    static long line(GameTestHelper h, int z) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.CHEST);
        h.setBlock(new BlockPos(7, 1, z), Blocks.CHEST);
        h.setBlock(new BlockPos(1, 1, z), ModBlocks.ITEM_PIPE.get());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(new BlockPos(1, 1, z)));
        pipe(h, new BlockPos(1, 1, z)).side(Direction.WEST).mode = SideMode.EXTRACT;
        long start = CurveTestSupport.node(h, new Vec3(2, 1.5, z + 0.5), new Anchor.PipeFace(new BlockPos(1, 1, z), Direction.EAST),
                PipeType.ITEM);
        long end = CurveTestSupport.node(h, new Vec3(7, 1.5, z + 0.5), new Anchor.BlockFace(new BlockPos(7, 1, z), Direction.WEST),
                PipeType.ITEM);
        return CurveTestSupport.edge(h, start, end, PipeType.ITEM, EAST, EAST);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void singleExtractDrivesTheCurvedLine(GameTestHelper h) {
        line(h, 4);
        chest(h, new BlockPos(0, 1, 4)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, new BlockPos(7, 1, 4), Items.DIAMOND) == 8, "the curved line carries the diamonds");
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void pipeFaceTurnsIntoAPipeConnection(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 1, 4);
        h.setBlock(at, ModBlocks.ITEM_PIPE.get());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(at));
        h.assertTrue(conn(h, at, Direction.EAST) == Conn.NONE, "nothing on the east face yet");
        long a = CurveTestSupport.node(h, new Vec3(2, 1.5, 4.5), new Anchor.PipeFace(at, Direction.EAST), PipeType.ITEM);
        long b = CurveTestSupport.node(h, new Vec3(6, 1.5, 4.5), Anchor.FREE, PipeType.ITEM);
        long edge = CurveTestSupport.edge(h, a, b, PipeType.ITEM, EAST, EndCondition.FREE);
        h.assertTrue(conn(h, at, Direction.EAST) == Conn.PIPE, "a curved pipe on the face connects like a pipe");
        CurveData.get(h.getLevel()).removeEdge(edge);
        h.assertTrue(conn(h, at, Direction.EAST) == Conn.NONE, "and lets go when it is removed");
        CurveTestSupport.clear(h);
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void curvedDistanceCountsTheLength(GameTestHelper h) {
        // block route: pipes x=1..4, chest C at x=5 (3 steps away); curved route: pipe 1's south face, 3 blocks to chest B
        BlockPos a = new BlockPos(0, 1, 4), c = new BlockPos(5, 1, 4), b = new BlockPos(1, 1, 8);
        h.setBlock(a, Blocks.CHEST);
        h.setBlock(c, Blocks.CHEST);
        h.setBlock(b, Blocks.CHEST);
        for (int x = 1; x <= 4; x++) h.setBlock(new BlockPos(x, 1, 4), ModBlocks.ITEM_PIPE.get());
        for (int x = 1; x <= 4; x++) PipeBlock.updateConnections(h.getLevel(), h.absolutePos(new BlockPos(x, 1, 4)));
        pipe(h, new BlockPos(1, 1, 4)).side(Direction.WEST).mode = SideMode.EXTRACT;
        long start = CurveTestSupport.node(h, new Vec3(1.5, 1.5, 5), new Anchor.PipeFace(new BlockPos(1, 1, 4), Direction.SOUTH),
                PipeType.ITEM);
        long end = CurveTestSupport.node(h, new Vec3(1.5, 1.5, 8), new Anchor.BlockFace(b, Direction.NORTH), PipeType.ITEM);
        CurveTestSupport.edge(h, start, end, PipeType.ITEM, SOUTH, SOUTH);
        chest(h, a).setItem(0, new ItemStack(Items.DIAMOND, 8));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, b, Items.DIAMOND) == 0, "the 3 block curve counts as 1 + 3, farther than 3 pipes");
            h.assertTrue(count(h, c, Items.DIAMOND) == 8, "the nearest chest takes everything");
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void removedEdgeStopsTheFlow(GameTestHelper h) {
        long edge = line(h, 4);
        BlockPos b = new BlockPos(7, 1, 4);
        chest(h, new BlockPos(0, 1, 4)).setItem(0, new ItemStack(Items.DIAMOND, 64));
        int[] seen = new int[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(h, b, Items.DIAMOND) > 0, "the flow starts"))
                .thenExecute(() -> {
                    CurveData.get(h.getLevel()).removeEdge(edge);
                    seen[0] = count(h, b, Items.DIAMOND);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(count(h, b, Items.DIAMOND) == seen[0], "nothing more arrives without the curve");
                    CurveTestSupport.clear(h);
                })
                .thenSucceed();
    }

    /**
     * Chest A at (0,1,z) with an extracting curved end on its east face, a straight curved edge to the west face of an
     * item pipe at (6,1,z), and chest B behind the pipe at (7,1,z). Returns the end's node id.
     */
    static long extractingEnd(GameTestHelper h, int z) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.CHEST);
        h.setBlock(new BlockPos(7, 1, z), Blocks.CHEST);
        h.setBlock(new BlockPos(6, 1, z), ModBlocks.ITEM_PIPE.get());
        PipeBlock.updateConnections(h.getLevel(), h.absolutePos(new BlockPos(6, 1, z)));
        long end = CurveTestSupport.node(h, new Vec3(1, 1.5, z + 0.5), new Anchor.BlockFace(new BlockPos(0, 1, z), Direction.EAST),
                PipeType.ITEM);
        long pipeFace = CurveTestSupport.node(h, new Vec3(6, 1.5, z + 0.5), new Anchor.PipeFace(new BlockPos(6, 1, z), Direction.WEST),
                PipeType.ITEM);
        CurveTestSupport.edge(h, end, pipeFace, PipeType.ITEM, EAST, EAST);
        CurveData.get(h.getLevel()).end(end).orElseThrow().cfg().mode = SideMode.EXTRACT;
        return end;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void curvedEndExtractsIntoBlockPipes(GameTestHelper h) {
        extractingEnd(h, 4);
        chest(h, new BlockPos(0, 1, 4)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, new BlockPos(7, 1, 4), Items.DIAMOND) == 8, "the curved end pulls into the block pipe");
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void curvedEndFollowsRedstone(GameTestHelper h) {
        long end = extractingEnd(h, 4);
        CurveData.get(h.getLevel()).end(end).orElseThrow().cfg().redstone = com.knozyy.flowline.pipe.RedstoneMode.REQUIRE_SIGNAL;
        chest(h, new BlockPos(0, 1, 4)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        BlockPos b = new BlockPos(7, 1, 4);
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(count(h, b, Items.DIAMOND) == 0, "no signal at the end: nothing moves");
                    h.setBlock(new BlockPos(1, 1, 3), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> h.assertTrue(count(h, b, Items.DIAMOND) == 8, "a signal next to the end starts it"))
                .thenExecute(() -> CurveTestSupport.clear(h))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unloadedCurvedEndIsSkipped(GameTestHelper h) {
        long far = CurveTestSupport.node(h, new Vec3(2001, 1.5, 0.5), new Anchor.BlockFace(new BlockPos(2000, 1, 0), Direction.EAST),
                PipeType.ITEM);
        var end = CurveData.get(h.getLevel()).end(far).orElseThrow();
        end.cfg().mode = SideMode.EXTRACT;
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(end.cfg().interval == -1, "an end in an unloaded chunk never ticks");
                    BlockPos block = end.block();
                    h.assertTrue(!h.getLevel().hasChunk(block.getX() >> 4, block.getZ() >> 4),
                            "and its chunk is not loaded to look at it");
                    CurveTestSupport.clear(h);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void curvedNetworksStayApart(GameTestHelper h) {
        line(h, 1);
        line(h, 7);
        chest(h, new BlockPos(0, 1, 1)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        chest(h, new BlockPos(0, 1, 7)).setItem(0, new ItemStack(Items.EMERALD, 8));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, new BlockPos(7, 1, 1), Items.DIAMOND) == 8
                    && count(h, new BlockPos(7, 1, 7), Items.EMERALD) == 8, "each line fills its own chest");
            h.assertTrue(count(h, new BlockPos(7, 1, 1), Items.EMERALD) == 0
                    && count(h, new BlockPos(7, 1, 7), Items.DIAMOND) == 0, "and nothing crosses over");
            CurveTestSupport.clear(h);
        });
    }
}
