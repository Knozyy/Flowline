package com.knozyy.flowline.pipe;

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
    ITEM("item", 4),
    FLUID("fluid", 200),
    ENERGY("energy", 1000);

    private final String name;
    /** Amount moved per operation at {@link SpeedTier#BASE}: items, mB or FE. */
    public final int baseAmount;

    PipeType(String name, int baseAmount) {
        this.name = name;
        this.baseAmount = baseAmount;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Whether the block at {@code pos} exposes this pipe's capability on {@code access}. */
    public boolean hasEndpoint(Level level, BlockPos pos, Direction access) {
        return switch (this) {
            case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, access) != null;
            case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, access) != null;
            case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, access) != null;
        };
    }

    public void transfer(Level level, BlockPos sourcePos, Direction sourceAccess, SideConfig cfg,
                         List<PipeNetwork.Target> targets) {
        int amount = baseAmount * cfg.speed.multiplier;
        switch (this) {
            case ITEM -> ItemTransfer.run(level, sourcePos, sourceAccess, cfg, targets, amount);
            case FLUID -> FluidTransfer.run(level, sourcePos, sourceAccess, cfg, targets, amount);
            case ENERGY -> EnergyTransfer.run(level, sourcePos, sourceAccess, targets, amount);
        }
    }
}
