package com.knozyy.flowline.test;

import com.knozyy.flowline.compat.mekanism.MekanismChemicals;
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
