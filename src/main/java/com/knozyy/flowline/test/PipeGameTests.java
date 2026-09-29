package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.pipe.Conn;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    /** The block entity at a test-relative position, cast to what the caller expects. */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T be(GameTestHelper helper, BlockPos pos) {
        return (T) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static BlockPos target(int pipes) {
        return new BlockPos(pipes + 1, 1, 1);
    }

    /** Builds the line and sets the first pipe's side facing the source chest to extract. */
    private static PipeBlockEntity line(GameTestHelper helper, int pipes) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(pipes), Blocks.CHEST);
        for (int i = 1; i <= pipes; i++) helper.setBlock(new BlockPos(i, 1, 1), ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity first = be(helper, FIRST_PIPE);
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
        return be(helper, pos);
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
        FilterEntry entry = FilterEntry.CODEC.parse(NbtOps.INSTANCE, legacy).result().orElseThrow();
        helper.assertTrue(entry.tags().equals(List.of("minecraft:logs")), "legacy tag target becomes a tag");
        helper.assertTrue(entry.item().isEmpty() && entry.invert(), "other fields survive");

        CompoundTag legacyItem = new CompoundTag();
        legacyItem.putString("target", "minecraft:stone");
        FilterEntry itemEntry = FilterEntry.CODEC.parse(NbtOps.INSTANCE, legacyItem).result().orElseThrow();
        helper.assertTrue(itemEntry.item().equals(Optional.of("minecraft:stone")), "legacy id target becomes an item");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void nbtRuleMatchesComponents(GameTestHelper helper) {
        SideConfig cfg = line(helper, 1).side(Direction.WEST);
        CompoundTag damaged = new CompoundTag();
        damaged.putInt("Damage", 5);   // 1.20.1 keeps damage in the stack NBT
        cfg.setEntry(0, FilterEntry.ofItem("minecraft:diamond_sword").withNbt(Optional.of(damaged)));
        ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
        worn.setDamageValue(5);
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

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void universalPipeMovesItems(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(2), Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.UNIVERSAL_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.UNIVERSAL_PIPE.get());
        PipeBlockEntity pipe = be(helper, FIRST_PIPE);
        pipe.side(Direction.WEST).mode = SideMode.EXTRACT;
        install(pipe, UpgradeType.STACK);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        helper.succeedWhen(() -> helper.assertTrue(count(helper, target(2), Items.IRON_INGOT) == 8,
                "universal pipes carry items"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void wrenchCyclesNormalExtractDisconnected(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(1), Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get());
        Player player = helper.makeMockPlayer();

        helper.startSequence()
                // connections are computed by a scheduled tick after placement: wait for it, not a fixed time
                .thenWaitUntil(() -> helper.assertTrue(westConn(helper) == Conn.ENDPOINT, "the pipe should connect"))
                .thenExecute(() -> {
                    PipeBlockEntity pipe = be(helper, FIRST_PIPE);
                    SideConfig cfg = pipe.side(Direction.WEST);
                    cycle(helper, pipe, player);
                    helper.assertTrue(cfg.mode == SideMode.EXTRACT, "1st: normal -> extract");
                    helper.assertTrue(westConn(helper) == Conn.EXTRACT, "extracting ends are drawn with the ring");
                    cycle(helper, pipe, player);
                    helper.assertTrue(pipe.isDisconnected(Direction.WEST) && cfg.mode == SideMode.INSERT,
                            "2nd: extract -> disconnected (and back to insert)");
                    helper.assertTrue(westConn(helper) == Conn.NONE, "disconnected ends are not drawn");
                    cycle(helper, pipe, player);
                    helper.assertTrue(!pipe.isDisconnected(Direction.WEST) && cfg.mode == SideMode.INSERT,
                            "3rd: disconnected -> normal");
                    helper.assertTrue(westConn(helper) == Conn.ENDPOINT, "normal ends are plain again");
                })
                .thenSucceed();
    }

    private static Conn westConn(GameTestHelper helper) {
        return helper.getBlockState(FIRST_PIPE).getValue(PipeBlock.prop(Direction.WEST));
    }

    private static void cycle(GameTestHelper helper, PipeBlockEntity pipe, Player player) {
        PipeBlock.cycleSide(pipe, Direction.WEST, westConn(helper), player);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void duplicateRulesAreDetected(GameTestHelper helper) {
        helper.assertTrue(allow("minecraft:dirt").sameMatch(block("minecraft:dirt")),
                "allow and block of the same item are the same rule");
        helper.assertTrue(FilterEntry.ofTags(false, "minecraft:logs", "minecraft:planks")
                .sameMatch(FilterEntry.ofTags(false, "minecraft:planks", "minecraft:logs")), "tag order does not matter");
        helper.assertTrue(!FilterEntry.ofTags(false, "minecraft:logs", "minecraft:planks")
                .sameMatch(FilterEntry.ofTags(true, "minecraft:logs", "minecraft:planks")), "OR and AND differ");
        helper.assertTrue(!allow("minecraft:dirt").sameMatch(allow("minecraft:stone")), "different items differ");
        helper.succeed();
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
    public static void itemsStartAt16AndDouble(GameTestHelper helper) {
        int[] expected = {16, 32, 64, 128, 256, 512, 1024};
        for (int stacks = 0; stacks < expected.length; stacks++) {
            helper.assertTrue(Pacing.itemsPerOperation(stacks) == expected[stacks],
                    stacks + " Stack upgrades: " + expected[stacks] + " items, got " + Pacing.itemsPerOperation(stacks));
        }
        helper.assertTrue(Pacing.itemsPerOperation(20) == 1024, "more upgrades stay at the last step");
        helper.assertTrue(Pacing.fluidPerOperation(0) == 1000 && Pacing.fluidPerOperation(6) == 64000,
                "fluids have their own curve: 1 to 64 buckets");
        helper.assertTrue(Pacing.chemicalPerOperation(0) == 1000 && Pacing.chemicalPerOperation(6) == 64000,
                "chemicals have their own curve: 1 to 64 buckets");
        helper.assertTrue(Pacing.stackMultiplier(6) == 128, "energy keeps its multipliers");
        helper.succeed();
    }

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
        // enough for the dozen or so busy operations it takes to reach the minimum interval
        for (int slot = 0; slot < 8; slot++) chest(helper, SOURCE).setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
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

    // ---- insert sides: priority, filter, regulator ----------------------------------------------------------

    private static final BlockPos NEAR = new BlockPos(1, 1, 0);   // barrel north of the first pipe (distance 0)
    private static final BlockPos FAR = new BlockPos(2, 1, 2);    // barrel south of the second pipe (distance 1)

    /** Source chest, two pipes, a near and a far barrel; the first pipe extracts from the chest. */
    private static PipeBlockEntity twoTargets(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(NEAR, Blocks.BARREL);
        helper.setBlock(FAR, Blocks.BARREL);
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity first = be(helper, FIRST_PIPE);
        first.side(Direction.WEST).mode = SideMode.EXTRACT;
        install(first, UpgradeType.STACK);
        return first;
    }

    private static SideConfig farSide(GameTestHelper helper) {
        return ((PipeBlockEntity) be(helper, new BlockPos(2, 1, 1))).side(Direction.SOUTH);
    }

    private static SideConfig nearSide(GameTestHelper helper) {
        return ((PipeBlockEntity) be(helper, FIRST_PIPE)).side(Direction.NORTH);
    }

    private static int stored(GameTestHelper helper, BlockPos pos, Item item) {
        return ((net.minecraft.world.Container) be(helper, pos)).countItem(item);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void priorityDistributionPrefersHigherPriority(GameTestHelper helper) {
        PipeBlockEntity pipe = twoTargets(helper);
        pipe.side(Direction.WEST).distribution = com.knozyy.flowline.pipe.Distribution.PRIORITY;
        farSide(helper).priority = 5;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, FAR, Items.DIAMOND) == 16,
                        "the far barrel has the higher priority and takes everything"))
                .thenExecute(() -> helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 0,
                        "the near barrel gets nothing while the far one accepts"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void insertFilterRoutesItems(GameTestHelper helper) {
        twoTargets(helper);
        nearSide(helper).setEntry(0, allow("minecraft:dirt"));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 4));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, NEAR, Items.DIRT) == 4
                        && stored(helper, FAR, Items.DIAMOND) == 4, "dirt goes near, diamonds go far"))
                .thenExecute(() -> helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 0,
                        "the near barrel only accepts dirt"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void insertLimitKeepsAtMost(GameTestHelper helper) {
        twoTargets(helper);
        nearSide(helper).limit = 3;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 8));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, FAR, Items.DIAMOND) == 5,
                        "what the near barrel may not take goes on"))
                .thenExecute(() -> helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 3,
                        "the near barrel stops at its limit of 3"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void extractLimitLeavesAtLeast(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).limit = 2;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 8));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 6, "6 of 8 move"))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(count(helper, SOURCE, Items.DIAMOND) == 2,
                        "the source keeps 2"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void balancedSplitsEvenly(GameTestHelper helper) {
        PipeBlockEntity pipe = twoTargets(helper);
        pipe.side(Direction.WEST).distribution = com.knozyy.flowline.pipe.Distribution.BALANCED;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, SOURCE, Items.DIAMOND) == 0, "all 16 move"))
                .thenExecute(() -> helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 8
                        && stored(helper, FAR, Items.DIAMOND) == 8, "8 and 8, got " + stored(helper, NEAR, Items.DIAMOND)
                        + " and " + stored(helper, FAR, Items.DIAMOND)))
                .thenSucceed();
    }

    // ---- redstone pulse -----------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void pulseMovesOncePerRisingEdge(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        pipe.side(Direction.WEST).redstone = RedstoneMode.PULSE;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 64));
        int perPulse = Pacing.itemsPerOperation(0);
        BlockPos lever = FIRST_PIPE.above();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 0, "no pulse, no transfer");
                    helper.setBlock(lever, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.DIAMOND) == perPulse,
                            "one pulse moves one operation, got " + count(helper, target(1), Items.DIAMOND));
                    helper.setBlock(lever, Blocks.AIR);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.setBlock(lever, Blocks.REDSTONE_BLOCK))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 2 * perPulse,
                        "a second pulse moves a second one"))
                .thenSucceed();
    }

    // ---- colours, channels, network cache -----------------------------------------------------------------

    private static void paint(GameTestHelper helper, BlockPos pos, int color) {
        PipeBlockEntity pipe = be(helper, pos);
        pipe.setColor(color);
        PipeBlock.updateConnections(helper.getLevel(), helper.absolutePos(pos));
        for (Direction dir : Direction.values()) {
            PipeBlock.updateConnections(helper.getLevel(), helper.absolutePos(pos).relative(dir));
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void differentColorsDoNotConnect(GameTestHelper helper) {
        install(line(helper, 2), UpgradeType.STACK);
        BlockPos second = new BlockPos(2, 1, 1);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockState(FIRST_PIPE).getValue(PipeBlock.prop(Direction.EAST)) == Conn.PIPE,
                        "the two pipes should connect first"))
                .thenExecute(() -> {
                    paint(helper, FIRST_PIPE, net.minecraft.world.item.DyeColor.RED.getId());
                    paint(helper, second, net.minecraft.world.item.DyeColor.BLUE.getId());
                    helper.assertTrue(helper.getBlockState(FIRST_PIPE).getValue(PipeBlock.prop(Direction.EAST)) == Conn.NONE,
                            "red and blue pipes must not connect");
                    // only now: the side may already run in the first ticks, before the pipes are painted
                    chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 4));
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(2), Items.DIAMOND) == 0, "nothing crosses a colour border");
                    paint(helper, second, net.minecraft.world.item.DyeColor.RED.getId());
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(2), Items.DIAMOND) == 4,
                        "same colour: connected again"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void universalChannelCanBeSwitchedOff(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(1), Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.UNIVERSAL_PIPE.get());
        PipeBlockEntity pipe = be(helper, FIRST_PIPE);
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.mode = SideMode.EXTRACT;
        cfg.channels = com.knozyy.flowline.pipe.PipeType.ALL_CHANNELS & ~com.knozyy.flowline.pipe.PipeType.CH_ITEMS;
        install(pipe, UpgradeType.STACK);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, target(1), Items.IRON_INGOT) == 0, "items channel is off");
                    cfg.channels = com.knozyy.flowline.pipe.PipeType.ALL_CHANNELS;
                    cfg.wake();
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.IRON_INGOT) == 8,
                        "items move once the channel is on"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void networkCacheIsLocal(GameTestHelper helper) {
        line(helper, 1);
        BlockPos lonely = new BlockPos(4, 1, 2);
        helper.setBlock(lonely, ModBlocks.ITEM_PIPE.get());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var graph = com.knozyy.flowline.pipe.PipeNetwork.graphAt(helper.getLevel(),
                            helper.absolutePos(FIRST_PIPE), com.knozyy.flowline.pipe.PipeType.ITEM);
                    helper.assertTrue(graph.valid() && graph.size() == 1, "one pipe in the first network");
                    helper.setBlock(new BlockPos(5, 1, 2), ModBlocks.ITEM_PIPE.get());
                    helper.assertTrue(graph.valid(), "a change elsewhere keeps this network cached");
                    helper.setBlock(new BlockPos(1, 1, 2), ModBlocks.ITEM_PIPE.get());
                    helper.assertTrue(!graph.valid(), "a pipe placed next to it invalidates it");
                    var rebuilt = com.knozyy.flowline.pipe.PipeNetwork.graphAt(helper.getLevel(),
                            helper.absolutePos(FIRST_PIPE), com.knozyy.flowline.pipe.PipeType.ITEM);
                    helper.assertTrue(rebuilt.valid() && rebuilt != graph, "the next lookup rebuilds it");
                })
                .thenSucceed();
    }

    // ---- rule types ---------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void modNameAndDurabilityRules(GameTestHelper helper) {
        SideConfig cfg = new SideConfig(com.knozyy.flowline.pipe.PipeType.ITEM);
        cfg.setEntry(0, FilterEntry.ofMod("minecraft"));
        helper.assertTrue(cfg.allowsItem(new ItemStack(Items.DIRT)), "@minecraft matches dirt");
        helper.assertTrue(!cfg.allowsItem(new ItemStack(ModItems.WRENCH.get())), "@minecraft skips the wrench");

        cfg.clearFilter();
        cfg.setEntry(0, FilterEntry.ofName("^diam"));
        helper.assertTrue(cfg.allowsItem(new ItemStack(Items.DIAMOND)), "name ^diam matches Diamond");
        helper.assertTrue(!cfg.allowsItem(new ItemStack(Items.DIRT)), "name ^diam does not match Dirt");
        ItemStack named = new ItemStack(Items.DIRT);
        named.setHoverName(net.minecraft.network.chat.Component.literal("Diamond dirt"));
        helper.assertTrue(cfg.allowsItem(named), "the display name counts, renames included");

        cfg.clearFilter();
        cfg.setEntry(0, FilterEntry.ofDurability(0, 50));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() * 3 / 4);
        helper.assertTrue(cfg.allowsItem(worn), "25% left is inside 0..50");
        helper.assertTrue(!cfg.allowsItem(new ItemStack(Items.IRON_PICKAXE)), "a new pickaxe is at 100%");
        helper.assertTrue(!cfg.allowsItem(new ItemStack(Items.DIRT)), "dirt has no durability");

        helper.assertTrue(FilterEntry.ofName("[").problem(com.knozyy.flowline.pipe.PipeType.ITEM) != null,
                "a broken pattern is rejected");
        helper.assertTrue(FilterEntry.ofMod("no_such_mod").problem(com.knozyy.flowline.pipe.PipeType.ITEM) != null,
                "an unknown mod is rejected");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void universalFluidRulesOnlyAffectFluids(GameTestHelper helper) {
        SideConfig cfg = new SideConfig(com.knozyy.flowline.pipe.PipeType.UNIVERSAL);
        cfg.setEntry(0, FilterEntry.ofItem("minecraft:water").withFluid(true));
        helper.assertTrue(cfg.allowsItem(new ItemStack(Items.DIRT)), "a fluid rule does not filter items");
        helper.assertTrue(cfg.allowsFluid(new net.minecraftforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 100)), "water is allowed");
        helper.assertTrue(!cfg.allowsFluid(new net.minecraftforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.LAVA, 100)), "lava is not");
        helper.succeed();
    }

    // ---- cards, facades, water ----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void configCardCopiesAndPastes(GameTestHelper helper) {
        PipeBlockEntity pipe = twoTargets(helper);
        SideConfig from = pipe.side(Direction.WEST);
        from.distribution = com.knozyy.flowline.pipe.Distribution.BALANCED;
        from.limit = 7;
        from.setEntry(0, allow("minecraft:diamond"));
        Player player = helper.makeMockPlayer();
        ItemStack card = new ItemStack(ModItems.CONFIG_CARD.get());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    ModItems.CONFIG_CARD.get().useOnPipe(pipe, Direction.WEST, Conn.EXTRACT, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, card);
                    player.setShiftKeyDown(false);
                    PipeBlockEntity other = be(helper, new BlockPos(2, 1, 1));
                    ModItems.CONFIG_CARD.get().useOnPipe(other, Direction.SOUTH, Conn.ENDPOINT, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, card);
                    SideConfig to = other.side(Direction.SOUTH);
                    helper.assertTrue(to.mode == SideMode.EXTRACT, "the mode is pasted");
                    helper.assertTrue(to.distribution == com.knozyy.flowline.pipe.Distribution.BALANCED && to.limit == 7,
                            "settings are pasted");
                    helper.assertTrue(to.getEntry(0) != null && to.getEntry(0).sameMatch(allow("minecraft:diamond")),
                            "rules are pasted");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void facadeMakesFullBlockAndDrops(GameTestHelper helper) {
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity pipe = be(helper, FIRST_PIPE);
        helper.assertTrue(com.knozyy.flowline.item.FacadeItem.isValid(Blocks.STONE.defaultBlockState()), "stone is a facade");
        helper.assertTrue(!com.knozyy.flowline.item.FacadeItem.isValid(Blocks.CHEST.defaultBlockState()),
                "chests are not");
        pipe.setFacade(Blocks.STONE.defaultBlockState());
        helper.assertTrue(helper.getBlockState(FIRST_PIPE).getShape(helper.getLevel(), helper.absolutePos(FIRST_PIPE))
                .equals(net.minecraft.world.phys.shapes.Shapes.block()), "a facaded pipe is a full block");
        helper.destroyBlock(FIRST_PIPE);
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.FACADE.get(), FIRST_PIPE, 2.0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pipesCanBeWaterlogged(GameTestHelper helper) {
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get().defaultBlockState().setValue(PipeBlock.WATERLOGGED, true));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(FIRST_PIPE).getValue(PipeBlock.WATERLOGGED),
                            "the connection refresh keeps the water");
                    helper.assertTrue(helper.getBlockState(FIRST_PIPE).getFluidState().isSource(), "it holds water");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void sleepsWithoutTargets(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity pipe = be(helper, FIRST_PIPE);
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
