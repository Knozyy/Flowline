package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.pipe.transfer.EnergyTransfer;
import com.knozyy.flowline.pipe.transfer.FluidTransfer;
import com.knozyy.flowline.pipe.transfer.ItemTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

public enum PipeType implements StringRepresentable {
    ITEM("item", true, false, false),
    FLUID("fluid", false, true, false),
    ENERGY("energy", false, false, true),
    /** Moves items, fluids and energy at once. Its filter applies to items. */
    UNIVERSAL("universal", true, true, true);

    private final String name;
    private final boolean items, fluids, energy;

    PipeType(String name, boolean items, boolean fluids, boolean energy) {
        this.name = name;
        this.items = items;
        this.fluids = fluids;
        this.energy = energy;
    }

    public boolean movesItems() {
        return items;
    }

    public boolean movesFluids() {
        return fluids;
    }

    public boolean movesEnergy() {
        return energy;
    }

    /** Whether rule filters exist on this pipe (energy has nothing to filter). */
    public boolean hasFilter() {
        return items || fluids;
    }

    /** Whether filter rules name fluids (fluid pipes) rather than items. */
    public boolean filtersFluids() {
        return this == FLUID;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Whether the block at {@code pos} exposes any capability this pipe moves on {@code access}. */
    public boolean hasEndpoint(Level level, BlockPos pos, Direction access) {
        return items && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, access) != null
                || fluids && level.getCapability(Capabilities.FluidHandler.BLOCK, pos, access) != null
                || energy && level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, access) != null;
    }

    /**
     * Runs one operation for every kind this pipe moves.
     *
     * @return how much was moved in total (items + mB + FE); 0 means the operation found no work
     */
    public int transfer(Level level, BlockPos sourcePos, Caps source, SideConfig cfg, List<PipeNetwork.Target> targets) {
        int multiplier = Pacing.stackMultiplier(cfg.stackCount);
        int moved = 0;
        if (items) {
            moved += ItemTransfer.run(level, sourcePos, source, cfg, targets,
                    FlowlineConfig.ITEMS_PER_OPERATION.get() * multiplier);
        }
        if (fluids) {
            moved += FluidTransfer.run(level, source, cfg, targets,
                    FlowlineConfig.FLUID_PER_OPERATION.get() * multiplier, filtersFluids());
        }
        if (energy) {
            moved += EnergyTransfer.run(source, targets, FlowlineConfig.ENERGY_PER_OPERATION.get() * multiplier);
        }
        return moved;
    }
}
