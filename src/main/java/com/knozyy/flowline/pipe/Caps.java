package com.knozyy.flowline.pipe;

import com.knozyy.flowline.compat.ChemicalCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge capability caches of one block face, one per kind of resource the pipe type moves (null for the others).
 * NeoForge invalidates them when the block changes, so transfers never look the block entity up again.
 * The chemical cache is untyped so this class loads without Mekanism.
 */
public record Caps(@Nullable BlockCapabilityCache<IItemHandler, Direction> items,
                   @Nullable BlockCapabilityCache<IFluidHandler, Direction> fluids,
                   @Nullable BlockCapabilityCache<IEnergyStorage, Direction> energy,
                   @Nullable BlockCapabilityCache<?, Direction> chemicals) {

    public static Caps create(PipeType type, ServerLevel level, BlockPos pos, Direction access) {
        return new Caps(
                type.movesItems() ? BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, pos, access) : null,
                type.movesFluids() ? BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, level, pos, access) : null,
                type.movesEnergy() ? BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, pos, access) : null,
                type.movesChemicals() ? ChemicalCompat.cache(level, pos, access) : null);
    }

    @Nullable
    public IItemHandler itemHandler() {
        return items == null ? null : items.getCapability();
    }

    @Nullable
    public IFluidHandler fluidHandler() {
        return fluids == null ? null : fluids.getCapability();
    }

    @Nullable
    public IEnergyStorage energyStorage() {
        return energy == null ? null : energy.getCapability();
    }

    /** The chemical handler as an Object; only {@link ChemicalCompat} casts it. */
    @Nullable
    public Object chemicalHandler() {
        return chemicals == null ? null : chemicals.getCapability();
    }
}
