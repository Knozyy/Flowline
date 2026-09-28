package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

public final class EnergyTransfer {
    private EnergyTransfer() {}

    /** @return FE moved. */
    public static int run(BlockCapabilityCache<?, Direction> sourceCache, List<Target> targets, int budget) {
        if (!(sourceCache.getCapability() instanceof IEnergyStorage source) || !source.canExtract()) return 0;

        int available = Math.min(budget, source.extractEnergy(budget, true));
        int remaining = available;
        for (Target t : targets) {
            if (remaining <= 0) break;
            if (!(t.cache().getCapability() instanceof IEnergyStorage dest) || dest == source || !dest.canReceive()) {
                continue;
            }

            int accepted = dest.receiveEnergy(remaining, true);
            if (accepted <= 0) continue;
            int extracted = source.extractEnergy(accepted, false);
            if (extracted <= 0) break;
            remaining -= dest.receiveEnergy(extracted, false);
        }
        return available - remaining;
    }
}
