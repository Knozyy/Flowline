package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.knozyy.flowline.pipe.Caps;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

public final class EnergyTransfer {
    private EnergyTransfer() {}

    /** @return FE moved. */
    public static int run(Caps sourceCaps, List<Target> targets, int budget) {
        IEnergyStorage source = sourceCaps.energyStorage();
        if (source == null || !source.canExtract()) return 0;

        int available = Math.min(budget, source.extractEnergy(budget, true));
        int remaining = available;
        for (Target t : targets) {
            if (remaining <= 0) break;
            IEnergyStorage dest = t.caps().energyStorage();
            if (dest == null || dest == source || !dest.canReceive()) {
                continue;
            }

            int accepted = dest.receiveEnergy(remaining, true);
            if (accepted <= 0) continue;
            int extracted = source.extractEnergy(accepted, false);
            if (extracted <= 0) break;
            int received = dest.receiveEnergy(extracted, false);
            // the target took less than it promised: give the rest back instead of losing it
            if (received < extracted) source.receiveEnergy(extracted - received, false);
            remaining -= received;
        }
        return available - remaining;
    }
}
