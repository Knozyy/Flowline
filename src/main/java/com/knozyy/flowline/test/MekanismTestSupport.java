package com.knozyy.flowline.test;

import com.knozyy.flowline.compat.mekanism.MekanismChemicals;
import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism-typed helpers of {@link MekanismGameTests}. Kept in their own class so the test holder never mentions a
 * Mekanism type and still loads when Mekanism is absent; this class is only touched when it is present.
 */
final class MekanismTestSupport {
    private MekanismTestSupport() {}

    private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(2, 1, 1);

    /** Source and target holding gas, one chemical pipe between them with its west side set to Extract. */
    private record Line(MekanismTestSupport.Tanks source, MekanismTestSupport.Tanks target, PipeBlockEntity pipe) {}

    private static Line line(GameTestHelper h, int sourceTanks) {
        MekanismTestSupport.Tanks source = new MekanismTestSupport.Tanks(sourceTanks, 100_000);
        MekanismTestSupport.Tanks target = new MekanismTestSupport.Tanks(1, 100_000);
        place(h, SOURCE, new MekanismTestSupport.GasChest(h.absolutePos(SOURCE), source));
        place(h, TARGET, new MekanismTestSupport.GasChest(h.absolutePos(TARGET), target));
        h.setBlock(PIPE, ModBlocks.CHEMICAL_PIPE.get());
        PipeBlockEntity pipe = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(PIPE));
        pipe.side(Direction.WEST).mode = SideMode.EXTRACT;
        return new Line(source, target, pipe);
    }

    private static void place(GameTestHelper h, BlockPos pos, MekanismTestSupport.GasChest chest) {
        h.setBlock(pos, Blocks.CHEST);
        h.getLevel().removeBlockEntity(chest.getBlockPos());
        h.getLevel().setBlockEntity(chest);
    }

    static void chemicalPipeMovesGasBetweenTanks(GameTestHelper h) {
        Line line = line(h, 1);
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        h.succeedWhen(() -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 1000, "the target tank should receive all 1000 mB");
            h.assertTrue(line.source().amountOf(hydrogen) == 0, "the source tank should be empty");
        });
    }

    static void extractFilterSkipsBlockedChemicalInEarlierTank(GameTestHelper h) {
        Line line = line(h, 2);
        var oxygen = MekanismTestSupport.gas("oxygen");
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        line.source().setChemicalInTank(1, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        line.pipe().side(Direction.WEST).setEntry(0, FilterEntry.ofItem("mekanism:hydrogen"));
        h.succeedWhen(() -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 1000, "allowed hydrogen in the second tank must flow");
            h.assertTrue(line.source().amountOf(oxygen) == 1000 && line.target().amountOf(oxygen) == 0,
                    "oxygen is not on the Allow list and must stay in the source");
        });
    }

    static void insertFilterBlocksChemicalFromTarget(GameTestHelper h) {
        Line line = line(h, 1);
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        line.pipe().side(Direction.EAST).setEntry(0, FilterEntry.ofItem("mekanism:hydrogen").withInvert(true));
        h.runAfterDelay(120, () -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 0, "a Block rule on the insert side must refuse hydrogen");
            h.assertTrue(line.source().amountOf(hydrogen) == 1000, "nothing may leave the source");
            h.succeed();
        });
    }

    static void blockRuleByModStopsEveryChemicalOfThatMod(GameTestHelper h) {
        Line line = line(h, 1);
        var oxygen = MekanismTestSupport.gas("oxygen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        line.pipe().side(Direction.WEST).setEntry(0, FilterEntry.ofMod("mekanism").withInvert(true));
        h.runAfterDelay(120, () -> {
            h.assertTrue(line.target().amountOf(oxygen) == 0 && line.source().amountOf(oxygen) == 1000,
                    "a Block rule for the whole mod must stop its chemicals");
            h.succeed();
        });
    }

    static void filteredSourceCountsAsWorkOnlyForAllowedChemicals(GameTestHelper h) {
        Line line = line(h, 1);
        var oxygen = MekanismTestSupport.gas("oxygen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        var cfg = line.pipe().side(Direction.WEST);
        var caps = com.knozyy.flowline.pipe.Caps.create(PipeType.CHEMICAL, h.getLevel(), h.absolutePos(SOURCE),
                Direction.UP);
        h.assertTrue(PipeType.CHEMICAL.hasWork(caps, cfg), "oxygen with no rules is work");
        cfg.setEntry(0, FilterEntry.ofItem("mekanism:hydrogen"));
        h.assertTrue(!PipeType.CHEMICAL.hasWork(caps, cfg), "oxygen is not allowed, so there is nothing to do");
        h.succeed();
    }

    static void chemicalRulesValidateIdsAndRejectUnsupportedParts(GameTestHelper h) {
        h.assertTrue(FilterEntry.ofItem("mekanism:hydrogen").problem(PipeType.CHEMICAL) == null, "a real gas is valid");
        h.assertTrue("gui.flowline.editor.error.unknown_chemical".equals(
                FilterEntry.ofItem("mekanism:nonsense").problem(PipeType.CHEMICAL)), "an unknown id is refused");
        h.assertTrue("gui.flowline.editor.error.chemical_parts".equals(
                FilterEntry.ofTags(false, "forge:ores").problem(PipeType.CHEMICAL)), "tags do not apply to chemicals");
        h.assertTrue(FilterEntry.ofName("^hydro").problem(PipeType.CHEMICAL) == null, "name patterns are valid");
        h.assertTrue(FilterEntry.ofMod("mekanism").problem(PipeType.CHEMICAL) == null, "a loaded mod is valid");
        h.succeed();
    }

    static void chemicalPipeHasALootTableAndDropsItself(GameTestHelper h) {
        ResourceLocation table = new ResourceLocation(Flowline.MODID, "blocks/chemical_pipe");
        h.assertTrue(h.getLevel().getServer().getLootData().getLootTable(table) != LootTable.EMPTY,
                "flowline:blocks/chemical_pipe must be a loaded loot table");
        h.setBlock(PIPE, ModBlocks.CHEMICAL_PIPE.get());
        BlockPos pos = h.absolutePos(PIPE);
        List<net.minecraft.world.item.ItemStack> drops = Block.getDrops(h.getLevel().getBlockState(pos), h.getLevel(),
                pos, h.getLevel().getBlockEntity(pos));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(ModBlocks.CHEMICAL_PIPE.get().asItem()),
                "breaking a chemical pipe drops one chemical pipe");
        h.succeed();
    }


    static Gas gas(String path) {
        Gas gas = MekanismAPI.gasRegistry().getValue(new ResourceLocation("mekanism", path));
        if (gas == null || gas.isEmptyType()) throw new IllegalStateException("no such gas: " + path);
        return gas;
    }

    /** A gas handler with {@code tanks} tanks of {@code capacity} each, holding any gas. */
    static final class Tanks implements IGasHandler {
        private final GasStack[] stacks;
        private final long capacity;

        Tanks(int tanks, long capacity) {
            this.stacks = new GasStack[tanks];
            java.util.Arrays.fill(stacks, GasStack.EMPTY);
            this.capacity = capacity;
        }

        @Override public int getTanks() { return stacks.length; }
        @Override public @NotNull GasStack getChemicalInTank(int tank) { return stacks[tank]; }
        @Override public void setChemicalInTank(int tank, @NotNull GasStack stack) { stacks[tank] = stack; }
        @Override public long getTankCapacity(int tank) { return capacity; }
        @Override public boolean isValid(int tank, @NotNull GasStack stack) { return true; }

        @Override
        public @NotNull GasStack insertChemical(int tank, @NotNull GasStack stack, @NotNull Action action) {
            if (stack.isEmpty()) return stack;
            GasStack current = stacks[tank];
            if (!current.isEmpty() && !current.isTypeEqual(stack)) return stack;
            long accepted = Math.min(capacity - current.getAmount(), stack.getAmount());
            if (accepted <= 0) return stack;
            if (action.execute()) {
                if (current.isEmpty()) stacks[tank] = new GasStack(stack.getType(), accepted);
                else current.grow(accepted);
            }
            if (accepted == stack.getAmount()) return GasStack.EMPTY;
            GasStack rest = stack.copy();
            rest.shrink(accepted);
            return rest;
        }

        @Override
        public @NotNull GasStack extractChemical(int tank, long amount, @NotNull Action action) {
            GasStack current = stacks[tank];
            if (current.isEmpty() || amount <= 0) return GasStack.EMPTY;
            long taken = Math.min(amount, current.getAmount());
            GasStack out = new GasStack(current.getType(), taken);
            if (action.execute()) current.shrink(taken);
            return out;
        }

        long amountOf(Gas gas) {
            long total = 0;
            for (GasStack stack : stacks) if (!stack.isEmpty() && stack.getType() == gas) total += stack.getAmount();
            return total;
        }
    }

    /** A chest that exposes {@link Tanks} as its gas capability. */
    static final class GasChest extends ChestBlockEntity {
        final Tanks tanks;
        private final LazyOptional<IGasHandler> handler;

        GasChest(BlockPos pos, Tanks tanks) {
            super(pos, Blocks.CHEST.defaultBlockState());
            this.tanks = tanks;
            this.handler = LazyOptional.of(() -> tanks);
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == MekanismChemicals.GAS ? handler.cast() : super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            handler.invalidate();
        }
    }
}
