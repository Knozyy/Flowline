package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** What happens to curved pipes when the blocks around them change. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveWorldGameTests {
    static final String TEMPLATE = CurveNetworkGameTests.TEMPLATE;

    /** A free straight edge 5 blocks long at height 2.5, along x through z = 4.5. */
    private static long airLine(GameTestHelper h) {
        long a = CurveTestSupport.node(h, new Vec3(1.5, 2.5, 4.5), Anchor.FREE, PipeType.ITEM);
        long b = CurveTestSupport.node(h, new Vec3(6.5, 2.5, 4.5), Anchor.FREE, PipeType.ITEM);
        return CurveTestSupport.edge(h, a, b, PipeType.ITEM, EndCondition.FREE, EndCondition.FREE);
    }

    private static int droppedPipes(GameTestHelper h, BlockPos around) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(around)).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModItems.ITEM_PIPE.get())).mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void blockPlacedInTheCurveBreaksIt(GameTestHelper h) {
        long edge = airLine(h);
        CurveData data = CurveData.get(h.getLevel());
        int cost = data.graph().edge(edge).orElseThrow().shape().cost();
        h.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        h.succeedWhen(() -> {
            h.assertTrue(data.graph().edge(edge).isEmpty(), "a block in the curve's path breaks it");
            h.assertTrue(droppedPipes(h, new BlockPos(4, 2, 4)) == cost, "and its " + cost + " pipes drop there");
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void silentBlockChangeBreaksCurves(GameTestHelper h) {
        long edge = airLine(h);
        CurveData data = CurveData.get(h.getLevel());
        // flag 2 only: the change tells no neighbour, like a piston's moving block
        h.getLevel().setBlock(h.absolutePos(new BlockPos(3, 2, 4)), Blocks.STONE.defaultBlockState(), 2);
        h.succeedWhen(() -> {
            h.assertTrue(data.graph().edge(edge).isEmpty(), "a quiet block change in the curve's path breaks it too");
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void itemsLandOnCurvedPipes(GameTestHelper h) {
        airLine(h);
        ItemEntity item = h.spawnItem(Items.STONE, 4f, 4f, 4.5f);
        double top = h.absoluteVec(new Vec3(0, 2.5 + 3 / 16.0, 0)).y;
        h.succeedWhen(() -> {
            h.assertTrue(item.onGround() && Math.abs(item.getY() - top) < 0.05,
                    "the item rests on the pipe's top at " + top + ", it is at " + item.getY());
            item.discard();
            CurveTestSupport.clear(h);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void blockBesideTheCurveKeepsIt(GameTestHelper h) {
        long edge = airLine(h);
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(CurveData.get(h.getLevel()).graph().edge(edge).isPresent(), "a block next to the curve leaves it");
                    CurveTestSupport.clear(h);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void pipeFaceLinkSurvivesPipeRemoval(GameTestHelper h) {
        long edge = CurveNetworkGameTests.line(h, 4);
        BlockPos pipe = new BlockPos(1, 1, 4);
        h.startSequence()
                .thenExecute(() -> h.setBlock(pipe, Blocks.AIR))
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(CurveData.get(h.getLevel()).graph().edge(edge).isPresent(), "the curve stays without its pipe");
                    h.setBlock(pipe, ModBlocks.ITEM_PIPE.get());
                })
                .thenWaitUntil(() -> h.assertTrue(CurveNetworkGameTests.conn(h, pipe, Direction.EAST) == Conn.PIPE,
                        "the pipe put back joins the curve again"))
                .thenExecute(() -> {
                    CurveNetworkGameTests.pipe(h, pipe).side(Direction.WEST).mode = SideMode.EXTRACT;
                    CurveNetworkGameTests.chest(h, new BlockPos(0, 1, 4)).setItem(0, new ItemStack(Items.DIAMOND, 4));
                })
                .thenWaitUntil(() -> h.assertTrue(CurveNetworkGameTests.count(h, new BlockPos(7, 1, 4), Items.DIAMOND) == 4,
                        "and the network flows through it"))
                .thenExecute(() -> CurveTestSupport.clear(h))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void endKeepsItsSettingsWhenTheChestGoes(GameTestHelper h) {
        long end = CurveNetworkGameTests.extractingEnd(h, 4);
        BlockPos chest = new BlockPos(0, 1, 4);
        h.startSequence()
                .thenExecute(() -> h.setBlock(chest, Blocks.AIR))
                .thenIdle(3)
                .thenExecute(() -> {
                    var kept = CurveData.get(h.getLevel()).end(end);
                    h.assertTrue(kept.isPresent() && kept.get().cfg().mode == SideMode.EXTRACT, "the end keeps its settings");
                    h.setBlock(chest, Blocks.CHEST);
                    CurveNetworkGameTests.chest(h, chest).setItem(0, new ItemStack(Items.DIAMOND, 4));
                })
                .thenWaitUntil(() -> h.assertTrue(CurveNetworkGameTests.count(h, new BlockPos(7, 1, 4), Items.DIAMOND) == 4,
                        "and pulls from the chest put back"))
                .thenExecute(() -> CurveTestSupport.clear(h))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void buildForMeGoesAroundCurvedPipes(GameTestHelper h) {
        long edge = airLine(h);
        h.assertTrue(com.knozyy.flowline.curved.CurveCollisions.occupies(h.getLevel(), h.absolutePos(new BlockPos(4, 2, 4))),
                "the curve passes through its cells");
        h.assertTrue(!com.knozyy.flowline.curved.CurveCollisions.occupies(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4))),
                "and not the ones below");
        Vec3 feet = h.absoluteVec(new Vec3(4.5, 1, 7.5));
        AABB player = new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
        java.util.List<BlockPos> path = com.knozyy.flowline.pipe.PipeBuilder.path(h.getLevel(),
                h.absolutePos(new BlockPos(4, 2, 1)), player, 32);
        h.assertTrue(path != null, "a route around the curve exists");
        for (BlockPos pos : path) {
            h.assertTrue(!com.knozyy.flowline.curved.CurveCollisions.occupies(h.getLevel(), pos),
                    "the route keeps out of the curve, but uses " + h.relativePos(pos));
        }
        h.assertTrue(CurveData.get(h.getLevel()).graph().edge(edge).isPresent(), "the curve is untouched");
        CurveTestSupport.clear(h);
        h.succeed();
    }
}
