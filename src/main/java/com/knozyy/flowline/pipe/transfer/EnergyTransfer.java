package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

public final class EnergyTransfer {
    private EnergyTransfer() {}

    public static void run(Level level, BlockPos sourcePos, Direction sourceAccess,
                           List<Target> targets, int budget) {
        IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK, sourcePos, sourceAccess);
        if (source == null || !source.canExtract()) return;

        int remaining = Math.min(budget, source.extractEnergy(budget, true));
        for (Target t : targets) {
            if (remaining <= 0) break;
            IEnergyStorage dest = level.getCapability(Capabilities.EnergyStorage.BLOCK, t.endpointPos(), t.access());
            if (dest == null || dest == source || !dest.canReceive()) continue;

            int accepted = dest.receiveEnergy(remaining, true);
            if (accepted <= 0) continue;
            int extracted = source.extractEnergy(accepted, false);
            if (extracted <= 0) break;
            remaining -= dest.receiveEnergy(extracted, false);
        }
    }
}
