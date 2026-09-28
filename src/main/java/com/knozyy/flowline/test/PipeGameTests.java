package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * In-world tests, run headless by {@code ./gradlew runGameTestServer} (and in CI). They assume the default config.
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

    private static ItemStack upgrade(UpgradeType type) {
        return new ItemStack(ModItems.upgrade(type));
    }

    private static void install(PipeBlockEntity pipe, UpgradeType... types) {
        for (UpgradeType type : types) pipe.installUpgrade(Direction.WEST, upgrade(type));
    }

    private static FilterEntry allow(String item) {
        return FilterEntry.ofItem(item);
    }

    private static FilterEntry block(String item) {
        return FilterEntry.ofItem(item).withInvert(true);
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        return chest(helper, pos).countItem(item);
    }

    // ---- transport ----------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void itemsFlowThroughPipes(GameTestHelper helper) {
        install(line(helper, 3), UpgradeType.STACK, UpgradeType.STACK);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target(3), Items.DIAMOND) == 16, "target should hold all 16 diamonds");
            helper.assertTrue(count(helper, SOURCE, Items.DIAMOND) == 0, "source should be empty");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void whitelistOnlyMovesListedItems(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.setEntry(0, allow("minecraft:diamond"));
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
        install(pipe, UpgradeType.STACK);
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.setEntry(0, block("minecraft:dirt"));
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
    public static void filterWorksWithoutUpgrades(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        cfg.setEntry(0, allow("minecraft:diamond"));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 1));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 1, "the diamond should be moved");
                    helper.assertTrue(count(helper, target(1), Items.DIRT) == 0, "dirt must not be moved");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tagRuleMatchesTagMembers(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).setEntry(0, FilterEntry.ofTags(false, "minecraft:logs"));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.OAK_LOG, 4));
        chest(helper, SOURCE).setItem(2, new ItemStack(Items.BIRCH_LOG, 4));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.OAK_LOG) == 4
                        && count(helper, target(1), Items.BIRCH_LOG) == 4, "every log should move"))
                .thenExecute(() -> helper.assertTrue(count(helper, target(1), Items.DIRT) == 0,
                        "dirt is not in #minecraft:logs"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tagsAnyMatchesEither(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).setEntry(0, FilterEntry.ofTags(false, "minecraft:logs", "minecraft:logs_that_burn"));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.OAK_LOG, 2));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.CRIMSON_STEM, 2));

        helper.succeedWhen(() -> helper.assertTrue(count(helper, target(1), Items.OAK_LOG) == 2
                && count(helper, target(1), Items.CRIMSON_STEM) == 2, "OR: logs from either tag move"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void tagsAllNeedsEvery(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).setEntry(0, FilterEntry.ofTags(true, "minecraft:logs", "minecraft:logs_that_burn"));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.OAK_LOG, 2));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.CRIMSON_STEM, 2));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.OAK_LOG) == 2,
                        "AND: oak logs are in both tags"))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(count(helper, target(1), Items.CRIMSON_STEM) == 0,
                        "AND: crimson stems do not burn, so they stay"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void legacyTargetStillLoads(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("target", "#minecraft:logs");
        legacy.putBoolean("invert", true);
        FilterEntry entry = FilterEntry.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        helper.assertTrue(entry.tags().equals(List.of("minecraft:logs")), "legacy tag target becomes a tag");
        helper.assertTrue(entry.item().isEmpty() && entry.invert(), "other fields survive");

        CompoundTag legacyItem = new CompoundTag();
        legacyItem.putString("target", "minecraft:stone");
        FilterEntry itemEntry = FilterEntry.CODEC.parse(NbtOps.INSTANCE, legacyItem).getOrThrow();
        helper.assertTrue(itemEntry.item().equals(Optional.of("minecraft:stone")), "legacy id target becomes an item");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void nbtRuleMatchesComponents(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        CompoundTag damaged = new CompoundTag();
        damaged.putInt("minecraft:damage", 5);
        cfg.setEntry(0, FilterEntry.ofItem("minecraft:diamond_sword").withNbt(Optional.of(damaged)));
        ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
        worn.set(DataComponents.DAMAGE, 5);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        chest(helper, SOURCE).setItem(1, worn);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND_SWORD) == 1,
                        "the damaged sword should move"))
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND_SWORD) == 1, "the new sword must stay");
                    helper.assertTrue(chest(helper, target(1)).getItem(0).getDamageValue() == 5,
                            "the moved sword is the damaged one");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void redstoneSignalGatesExtraction(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).redstone = RedstoneMode.REQUIRE_SIGNAL;
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

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void wrenchDisconnectStopsFlow(GameTestHelper helper) {
        install(line(helper, 2), UpgradeType.STACK);
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

    // ---- upgrades -----------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void upgradesCountPerSide(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK, UpgradeType.STACK, UpgradeType.KNOZY, UpgradeType.FILTER);
        SideConfig cfg = pipe.side(Direction.WEST);
        helper.assertTrue(cfg.stackCount == 3, "2 Stack + 1 Knozy = 3 stack, got " + cfg.stackCount);
        helper.assertTrue(cfg.speedCount == 1, "1 Knozy = 1 speed, got " + cfg.speedCount);
        helper.assertTrue(cfg.filterCount == 2, "1 Filter + 1 Knozy = 2 filter, got " + cfg.filterCount);

        for (int i = 4; i < SideConfig.UPGRADE_SLOTS; i++) install(pipe, UpgradeType.SPEED);
        helper.assertTrue(!pipe.installUpgrade(Direction.WEST, upgrade(UpgradeType.SPEED)), "a 7th upgrade must not fit");

        List<ItemStack> removed = pipe.removeAllUpgrades(Direction.WEST);
        helper.assertTrue(removed.size() == SideConfig.UPGRADE_SLOTS, "removing returns all six upgrades");
        helper.assertTrue(cfg.stackCount == 0 && cfg.speedCount == 0 && cfg.filterCount == 0,
                "removing resets the counts");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void brokenPipeDropsUpgrades(GameTestHelper helper) {
        install(line(helper, 1), UpgradeType.SPEED, UpgradeType.STACK);
        helper.destroyBlock(FIRST_PIPE);
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(ModItems.SPEED_UPGRADE.get(), FIRST_PIPE, 2.0);
            helper.assertItemEntityPresent(ModItems.STACK_UPGRADE.get(), FIRST_PIPE, 2.0);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void filterUpgradesAddCapacity(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        SideConfig cfg = pipe.side(Direction.WEST);
        helper.assertTrue(cfg.filterCapacity() == 9, "9 entries by default, got " + cfg.filterCapacity());
        install(pipe, UpgradeType.FILTER);
        helper.assertTrue(cfg.filterCapacity() == 18, "one Filter upgrade adds 9, got " + cfg.filterCapacity());
        install(pipe, UpgradeType.KNOZY);
        helper.assertTrue(cfg.filterCapacity() == 27, "Knozy adds 9 more, got " + cfg.filterCapacity());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void entriesPastCapacityNeedAFilterUpgrade(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.setEntry(9, allow("minecraft:diamond"));   // the 10th entry: only usable with a Filter upgrade
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 4));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIRT) == 4,
                        "without a Filter upgrade the 10th entry is inactive, so everything moves"))
                .thenExecute(() -> {
                    install(pipe, UpgradeType.FILTER);
                    chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
                    chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 4));
                    chest(helper, target(1)).clearContent();
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 4,
                        "with the upgrade the whitelisted diamonds move"))
                .thenExecute(() -> helper.assertTrue(count(helper, target(1), Items.DIRT) == 0,
                        "with the upgrade dirt is filtered out"))
                .thenSucceed();
    }

    // ---- pacing -------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void speedLowersStart(GameTestHelper helper) {
        helper.assertTrue(Pacing.start(0) == 30, "default start is 30 ticks, got " + Pacing.start(0));
        helper.assertTrue(Pacing.start(2) == 22, "two Speed upgrades start at 22 ticks, got " + Pacing.start(2));
        helper.assertTrue(Pacing.start(6) == 6, "six Speed upgrades start at 6 ticks, got " + Pacing.start(6));
        helper.assertTrue(Pacing.start(100) == Pacing.min(), "never below the minimum interval");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void intervalAccelerates(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 64));
        helper.succeedWhen(() -> helper.assertTrue(cfg.interval == Pacing.min(),
                "steady work should bring the interval down to the minimum, now " + cfg.interval));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150)
    public static void intervalSlowsWhenIdle(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        helper.succeedWhen(() -> {
            helper.assertTrue(cfg.interval > Pacing.start(0), "an empty source should slow the side down");
            helper.assertTrue(!cfg.sleeping, "an empty source is polled slowly, not slept on");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void sleepsWithoutTargets(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity pipe = helper.getBlockEntity(FIRST_PIPE);
        pipe.side(Direction.WEST).mode = SideMode.EXTRACT;
        install(pipe, UpgradeType.STACK);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 4));
        SideConfig cfg = pipe.side(Direction.WEST);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cfg.sleeping, "no target: the side should sleep"))
                .thenExecute(() -> helper.setBlock(target(1), Blocks.CHEST))
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 4,
                        "a new target should wake the side up"))
                .thenSucceed();
    }
}
