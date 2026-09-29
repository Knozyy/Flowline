package com.knozyy.flowline.pipe;

import com.knozyy.flowline.compat.ChemicalCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Capability caches of one block face, one per kind of resource the pipe type moves (null for the others), so
 * transfers do not look the block entity up again. The chemical caches are untyped so this class loads without
 * Mekanism; only {@link ChemicalCompat} knows what they hold.
 */
public record Caps(@Nullable CapCache<IItemHandler> items,
                   @Nullable CapCache<IFluidHandler> fluids,
                   @Nullable CapCache<IEnergyStorage> energy,
                   @Nullable Object chemicals) {

    public static Caps create(PipeType type, ServerLevel level, BlockPos pos, Direction access) {
        return new Caps(
                type.movesItems() ? new CapCache<>(ForgeCapabilities.ITEM_HANDLER, level, pos, access) : null,
                type.movesFluids() ? new CapCache<>(ForgeCapabilities.FLUID_HANDLER, level, pos, access) : null,
                type.movesEnergy() ? new CapCache<>(ForgeCapabilities.ENERGY, level, pos, access) : null,
                type.movesChemicals() ? ChemicalCompat.cache(level, pos, access) : null);
    }

    @Nullable
    public IItemHandler itemHandler() {
        return items == null ? null : items.get();
    }

    @Nullable
    public IFluidHandler fluidHandler() {
        return fluids == null ? null : fluids.get();
    }

    @Nullable
    public IEnergyStorage energyStorage() {
        return energy == null ? null : energy.get();
    }
}
