package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SpeedTier;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests, run headless by {@code ./gradlew runGameTestServer} (and in CI).
 * Layout: source chest at x=0, item pipes at x=1..n, target chest at x=n+1, all on y=1, z=1.
 */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class PipeGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
    private static final BlockPos FIRST_PIPE = new BlockPos(1, 1, 1);

    private static BlockPos target(int pipes) {
        return new BlockPos(pipes + 1, 1, 1);
    }

    /** Builds the line and sets the first pipe's side facing the source chest to extract. */
    private static PipeBlockEntity line(GameTestHelper helper, int pipes) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(pipes), Blocks.CHEST);
        for (int i = 1; i <= pipes; i++) helper.setBlock(new BlockPos(i, 1, 1), ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity first = helper.getBlockEntity(FIRST_PIPE);
        first.side(Direction.WEST).mode = SideMode.EXTRACT;
        return first;
    }

    private static ItemStack upgrade(SpeedTier tier) {
        return new ItemStack(ModItems.SPEED_UPGRADES.get(tier.ordinal() - 1).get());
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        return chest(helper, pos).countItem(item);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void itemsFlowThroughPipes(GameTestHelper helper) {
        line(helper, 3);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target(3), Items.DIAMOND) == 16, "target should hold all 16 diamonds");
            helper.assertTrue(count(helper, SOURCE, Items.DIAMOND) == 0, "source should be empty");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void whitelistOnlyMovesListedItems(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        pipe.setUpgrade(Direction.WEST, upgrade(SpeedTier.BASIC));
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.whitelist = true;
        cfg.filter.add(new ItemStack(Items.DIAMOND));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 8));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 8));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 8, "diamonds should be moved");
                    helper.assertTrue(count(helper, target(1), Items.DIRT) == 0, "dirt must not be moved");
                    helper.assertTrue(count(helper, SOURCE, Items.DIRT) == 8, "dirt should stay in the source");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void blacklistBlocksListedItems(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        pipe.setUpgrade(Direction.WEST, upgrade(SpeedTier.BASIC));
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.whitelist = false;
        cfg.filter.add(new ItemStack(Items.DIRT));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 8));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 8));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 8, "diamonds should be moved");
                    helper.assertTrue(count(helper, target(1), Items.DIRT) == 0, "dirt must not be moved");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void filterNeedsAnUpgrade(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        cfg.whitelist = true;
        cfg.filter.add(new ItemStack(Items.DIAMOND));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));

        helper.succeedWhen(() -> helper.assertTrue(count(helper, target(1), Items.DIRT) == 4,
                "without an upgrade the filter is inactive, so dirt should move"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void redstoneSignalGatesExtraction(GameTestHelper helper) {
        line(helper, 1).side(Direction.WEST).redstone = RedstoneMode.REQUIRE_SIGNAL;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 4));

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 0, "nothing should move without signal");
                    helper.setBlock(FIRST_PIPE.above(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 4,
                        "items should move once powered"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void wrenchDisconnectStopsFlow(GameTestHelper helper) {
        line(helper, 2);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 4));
        Boolean connected = PipeBlock.toggleConnection(helper.getLevel(), helper.absolutePos(FIRST_PIPE), Direction.EAST);
        helper.assertTrue(Boolean.FALSE.equals(connected), "first toggle should cut the pipe connection");

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(2), Items.DIAMOND) == 0, "cut pipes must not transfer");
                    Boolean again = PipeBlock.toggleConnection(helper.getLevel(), helper.absolutePos(FIRST_PIPE),
                            Direction.EAST);
                    helper.assertTrue(Boolean.TRUE.equals(again), "second toggle should reconnect");
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(2), Items.DIAMOND) == 4,
                        "items should flow after reconnecting"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void upgradeSlotSetsSpeed(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        pipe.setUpgrade(Direction.WEST, upgrade(SpeedTier.KNOZY));
        helper.assertTrue(pipe.side(Direction.WEST).speed == SpeedTier.KNOZY, "installing sets the speed");
        ItemStack removed = pipe.removeUpgrade(Direction.WEST);
        helper.assertTrue(removed.is(ModItems.SPEED_UPGRADES.get(3).get()), "removing returns the upgrade");
        helper.assertTrue(pipe.side(Direction.WEST).speed == SpeedTier.BASE, "removing resets the speed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void brokenPipeDropsUpgrades(GameTestHelper helper) {
        line(helper, 1).setUpgrade(Direction.WEST, upgrade(SpeedTier.REGULAR));
        helper.destroyBlock(FIRST_PIPE);
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.SPEED_UPGRADES.get(1).get(), FIRST_PIPE, 2.0));
    }
}
