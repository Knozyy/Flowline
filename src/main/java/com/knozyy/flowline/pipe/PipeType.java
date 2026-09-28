package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.pipe.transfer.EnergyTransfer;
import com.knozyy.flowline.pipe.transfer.FluidTransfer;
import com.knozyy.flowline.pipe.transfer.ItemTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

public enum PipeType implements StringRepresentable {
    ITEM("item"),
    FLUID("fluid"),
    ENERGY("energy");

    private final String name;

    PipeType(String name) {
        this.name = name;
    }

    /** Amount moved per operation without Stack upgrades: items, mB or FE. Read from the config. */
    public int baseAmount() {
        return switch (this) {
            case ITEM -> FlowlineConfig.ITEMS_PER_OPERATION.get();
            case FLUID -> FlowlineConfig.FLUID_PER_OPERATION.get();
            case ENERGY -> FlowlineConfig.ENERGY_PER_OPERATION.get();
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The block capability this pipe type moves through. */
    public BlockCapability<?, Direction> capability() {
        return switch (this) {
            case ITEM -> Capabilities.ItemHandler.BLOCK;
            case FLUID -> Capabilities.FluidHandler.BLOCK;
            case ENERGY -> Capabilities.EnergyStorage.BLOCK;
        };
    }

    /** Whether the block at {@code pos} exposes this pipe's capability on {@code access}. */
    public boolean hasEndpoint(Level level, BlockPos pos, Direction access) {
        return switch (this) {
            case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, access) != null;
            case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, access) != null;
            case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, access) != null;
        };
    }

    /** @return how much was moved (items, mB or FE); 0 means the operation found no work. */
    /** @param source cached capability of the block being extracted from (see {@link SideConfig#sourceCache}). */
    public int transfer(Level level, BlockPos sourcePos, BlockCapabilityCache<?, Direction> source, SideConfig cfg,
                        List<PipeNetwork.Target> targets) {
        int amount = baseAmount() * Pacing.stackMultiplier(cfg.stackCount);
        return switch (this) {
            case ITEM -> ItemTransfer.run(level, sourcePos, source, cfg, targets, amount);
            case FLUID -> FluidTransfer.run(level, source, cfg, targets, amount);
            case ENERGY -> EnergyTransfer.run(source, targets, amount);
        };
    }
}
