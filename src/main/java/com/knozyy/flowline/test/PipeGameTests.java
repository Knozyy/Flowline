package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeBuilder;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.transfer.FluidTransfer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * In-world tests, run headless by {@code ./gradlew runGameTestServer} (and in CI). Expected numbers come from the
 * config; timeouts assume values near the defaults.
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
        int base = FlowlineConfig.BASE_FILTER_SLOTS.get(), per = FlowlineConfig.FILTER_SLOTS_PER_UPGRADE.get();
        helper.assertTrue(cfg.filterCapacity() == base, "base entries without upgrades, got " + cfg.filterCapacity());
        install(pipe, UpgradeType.FILTER);
        helper.assertTrue(cfg.filterCapacity() == base + per, "a Filter upgrade adds entries, got "
                + cfg.filterCapacity());
        install(pipe, UpgradeType.KNOZY);
        helper.assertTrue(cfg.filterCapacity() == base + 2 * per, "Knozy adds as many, got " + cfg.filterCapacity());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void entriesPastCapacityNeedAFilterUpgrade(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        SideConfig cfg = pipe.side(Direction.WEST);
        cfg.setEntry(cfg.filterCapacity(), allow("minecraft:diamond"));   // first entry past capacity: needs a Filter
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIRT, 4));
        chest(helper, SOURCE).setItem(1, new ItemStack(Items.DIAMOND, 4));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIRT) == 4,
                        "without a Filter upgrade the entry past capacity is inactive, so everything moves"))
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
    public static void stackUpgradesFollowTheirCurves(GameTestHelper helper) {
        checkCurve(helper, "items", Pacing::itemsPerOperation, FlowlineConfig.ITEMS_PER_OPERATION.get(),
                FlowlineConfig.ITEM_STACK_MULTIPLIERS.get());
        checkCurve(helper, "fluid", Pacing::fluidPerOperation, FlowlineConfig.FLUID_PER_OPERATION.get(),
                FlowlineConfig.FLUID_STACK_MULTIPLIERS.get());
        checkCurve(helper, "chemicals", Pacing::chemicalPerOperation, FlowlineConfig.CHEMICAL_PER_OPERATION.get(),
                FlowlineConfig.CHEMICAL_STACK_MULTIPLIERS.get());
        checkCurve(helper, "energy multiplier", Pacing::stackMultiplier, 1, FlowlineConfig.STACK_MULTIPLIERS.get());
        helper.succeed();
    }

    /** Entry i of the curve is base x multipliers[i] (capped), and more upgrades stay at the last step. */
    private static void checkCurve(GameTestHelper helper, String kind, IntUnaryOperator perOperation,
                                   int base, List<? extends Integer> multipliers) {
        int steps = Math.max(1, multipliers.size());
        for (int stacks = 0; stacks < steps + 2; stacks++) {
            int multiplier = multipliers.isEmpty() ? 1 : multipliers.get(Math.min(stacks, steps - 1));
            long expected = Math.min(Integer.MAX_VALUE, (long) base * Math.max(1, multiplier));
            helper.assertTrue(perOperation.applyAsInt(stacks) == expected, kind + " with " + stacks
                    + " Stack upgrades should be " + expected + ", got " + perOperation.applyAsInt(stacks));
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void speedLowersStart(GameTestHelper helper) {
        int start = FlowlineConfig.START_INTERVAL.get(), reduction = FlowlineConfig.SPEED_REDUCTION.get();
        for (int speed : new int[] {0, 2, 6}) {
            int expected = Math.max(Pacing.min(), start - speed * reduction);
            helper.assertTrue(Pacing.start(speed) == expected,
                    speed + " Speed upgrades should start at " + expected + " ticks, got " + Pacing.start(speed));
        }
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

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void fullFluidTargetDoesNotDrainTheSource(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(target(1), Blocks.CHEST);
        helper.setBlock(FIRST_PIPE, ModBlocks.FLUID_PIPE.get());
        TestFluidChest source = new TestFluidChest(helper.absolutePos(SOURCE));
        TestFluidChest destination = new TestFluidChest(helper.absolutePos(target(1)));
        helper.getLevel().removeBlockEntity(source.getBlockPos());
        helper.getLevel().removeBlockEntity(destination.getBlockPos());
        helper.getLevel().setBlockEntity(source);
        helper.getLevel().setBlockEntity(destination);
        source.tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        PipeBlockEntity pipe = be(helper, FIRST_PIPE);
        Caps sourceCaps = Caps.create(PipeType.FLUID, helper.getLevel(), source.getBlockPos(), Direction.EAST);
        PipeNetwork.Target target = new PipeNetwork.Target(pipe.getBlockPos(), Direction.EAST, 0,
                Caps.create(PipeType.FLUID, helper.getLevel(), destination.getBlockPos(), Direction.WEST), pipe,
                List.of(pipe.getBlockPos()));
        SideConfig cfg = new SideConfig(PipeType.FLUID);
        int moved = FluidTransfer.run(helper.getLevel(), sourceCaps, cfg, List.of(target), 1000, false, PipeType.FLUID);
        helper.assertTrue(moved == 1000 && source.tank.isEmpty() && destination.tank.getFluidAmount() == 1000,
                "the fluid moves to the target");
        source.tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        moved = FluidTransfer.run(helper.getLevel(), sourceCaps, cfg, List.of(target), 1000, false, PipeType.FLUID);
        helper.assertTrue(moved == 0 && source.tank.getFluidAmount() == 1000, "a full target must not drain fluid");
        helper.succeed();
    }

    private static final class TestFluidChest extends ChestBlockEntity {
        private final FluidTank tank = new FluidTank(1000);
        private final LazyOptional<IFluidHandler> fluids = LazyOptional.of(() -> tank);

        private TestFluidChest(BlockPos pos) { super(pos, Blocks.CHEST.defaultBlockState()); }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return capability == ForgeCapabilities.FLUID_HANDLER ? fluids.cast() : super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() { super.invalidateCaps(); fluids.invalidate(); }
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
    public static void ruleAmountKeepsItemsInTheSource(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.WEST).setEntry(0, allow("minecraft:diamond").withAmount(4));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 6,
                        "everything above the rule's amount moves"))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(count(helper, SOURCE, Items.DIAMOND) == 4,
                        "the extract rule's amount stays in the source"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void ruleAmountCapsTheTarget(GameTestHelper helper) {
        PipeBlockEntity pipe = line(helper, 1);
        install(pipe, UpgradeType.STACK);
        pipe.side(Direction.EAST).setEntry(0, allow("minecraft:diamond").withAmount(5));
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 5,
                        "the insert rule's amount fills the target"))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(count(helper, target(1), Items.DIAMOND) == 5
                        && count(helper, SOURCE, Items.DIAMOND) == 5, "and nothing goes past it"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void overflowTargetOnlyTakesTheRest(GameTestHelper helper) {
        twoTargets(helper);
        nearSide(helper).overflow = true;
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, FAR, Items.DIAMOND) == 16,
                        "the normal far barrel takes everything while it has room"))
                .thenExecute(() -> {
                    helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 0,
                            "the near overflow barrel gets nothing while the far one accepts");
                    net.minecraft.world.Container far = (net.minecraft.world.Container) be(helper, FAR);
                    for (int i = 0; i < far.getContainerSize(); i++) far.setItem(i, new ItemStack(Items.DIRT, 64));
                    chest(helper, SOURCE).setItem(0, new ItemStack(Items.DIAMOND, 16));
                })
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, NEAR, Items.DIAMOND) == 16,
                        "with the far barrel full, the rest goes to the overflow barrel"))
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

    // ---- cards, water ------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void anyHeldItemOpensASideLikeAChest(GameTestHelper helper) {
        PipeBlockEntity first = line(helper, 1);
        var level = helper.getLevel();
        BlockPos pos = first.getBlockPos();
        PipeBlock.updateConnections(level, pos);
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.getValue(PipeBlock.prop(Direction.WEST)).isEndpoint(), "the chest side is an endpoint");
        var hit = new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(pos.getX() + 0.1, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.WEST, pos, false);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
        helper.assertTrue(state.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction(),
                "a block in hand still opens the side, like a chest");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.ITEM_PIPE.get()));
        helper.assertTrue(state.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)
                == net.minecraft.world.InteractionResult.PASS, "a pipe in hand keeps laying pipes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlyPipesAttachedToBlocksTick(GameTestHelper helper) {
        PipeBlockEntity first = line(helper, 3);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    var type = com.knozyy.flowline.registry.ModBlockEntities.PIPE.get();
                    BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 1));
                    BlockState middleState = level.getBlockState(middle);
                    helper.assertTrue(((PipeBlock) middleState.getBlock()).getTicker(level, middleState, type) == null,
                            "a pipe that only carries (no block attached) must not tick");
                    BlockState firstState = level.getBlockState(first.getBlockPos());
                    helper.assertTrue(((PipeBlock) firstState.getBlock()).getTicker(level, firstState, type) != null,
                            "a pipe attached to a block ticks");
                    helper.assertTrue(first.side(Direction.WEST).interval >= 0, "the extracting side has run");
                    helper.setBlock(SOURCE, Blocks.AIR);
                    PipeBlock.updateConnections(level, first.getBlockPos());
                    BlockState now = level.getBlockState(first.getBlockPos());
                    helper.assertTrue(((PipeBlock) now.getBlock()).getTicker(level, now, type) == null,
                            "once its block is gone the pipe stops ticking");
                    helper.assertTrue(first.side(Direction.WEST).interval < 0 && !first.emitsSignal(),
                            "a pipe that stops ticking resets its sides and its redstone output");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void filterCardCopiesOnlyRules(GameTestHelper helper) {
        PipeBlockEntity pipe = twoTargets(helper);
        SideConfig from = pipe.side(Direction.WEST);
        from.distribution = com.knozyy.flowline.pipe.Distribution.BALANCED;
        from.limit = 7;
        from.setEntry(0, allow("minecraft:diamond"));
        Player player = helper.makeMockPlayer();
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    ModItems.FILTER_CARD.get().useOnPipe(pipe, Direction.WEST, Conn.EXTRACT, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, card);
                    player.setShiftKeyDown(false);
                    PipeBlockEntity other = be(helper, new BlockPos(2, 1, 1));
                    ModItems.FILTER_CARD.get().useOnPipe(other, Direction.SOUTH, Conn.ENDPOINT, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, card);
                    SideConfig to = other.side(Direction.SOUTH);
                    helper.assertTrue(to.getEntry(0) != null && to.getEntry(0).sameMatch(allow("minecraft:diamond")),
                            "rules are pasted");
                    helper.assertTrue(to.mode != SideMode.EXTRACT && to.limit != 7,
                            "the filter card leaves mode and settings alone");
                })
                .thenSucceed();
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

    // ---- build for me -------------------------------------------------------------------------------------

    /** A standing player's box in the cell at {@code feet}. */
    private static AABB playerAt(BlockPos feet) {
        return new AABB(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2, feet.getX() + 0.8, feet.getY() + 1.8,
                feet.getZ() + 0.8);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void buildPathGoesAroundWalls(GameTestHelper helper) {
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
        }
        BlockPos start = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos feet = helper.absolutePos(new BlockPos(5, 1, 1));
        List<BlockPos> path = PipeBuilder.path(helper.getLevel(), start, playerAt(feet), 64);
        helper.assertTrue(path != null && !path.isEmpty(), "a wall with a way around it must not block the path");
        helper.assertTrue(path.get(0).equals(start), "the path starts at the looked-at face");
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            helper.assertTrue(helper.getLevel().getBlockState(pos).canBeReplaced(), "the path runs through air: " + pos);
            helper.assertTrue(!pos.equals(feet) && !pos.equals(feet.above()), "no pipe where the player stands");
            if (i > 0) helper.assertTrue(pos.distManhattan(path.get(i - 1)) == 1, "the path is connected at " + pos);
        }
        BlockPos last = path.get(path.size() - 1);
        helper.assertTrue(last.distManhattan(feet) == 1 || last.distManhattan(feet.above()) == 1,
                "the path ends next to the player, not at " + last);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void buildPathRefusesWhenWalledIn(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, 1, 1);
        for (Direction dir : Direction.values()) helper.setBlock(start.relative(dir), Blocks.STONE);
        List<BlockPos> path = PipeBuilder.path(helper.getLevel(), helper.absolutePos(start),
                playerAt(helper.absolutePos(new BlockPos(5, 1, 1))), 64);
        helper.assertTrue(path == null, "a walled-in start has no path");
        helper.succeed();
    }
}
