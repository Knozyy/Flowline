package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

public final class EnergyTransfer {
    private EnergyTransfer() {}

    private record Dest(IEnergyStorage storage, SideConfig cfg) {}

    /** @return FE moved. */
    public static int run(Caps sourceCaps, SideConfig cfg, List<Target> targets, int budget, boolean balanced,
                          PipeType pipe) {
        IEnergyStorage source = sourceCaps.energyStorage();
        if (source == null || !source.canExtract()) return 0;

        int available = Math.min(budget, source.extractEnergy(budget, true));
        // regulator: leave at least `limit` FE in the source
        if (cfg.limit > 0) available = Math.min(available, source.getEnergyStored() - cfg.limit);
        if (available <= 0) return 0;

        List<Dest> destinations = new ArrayList<>();
        for (Target t : targets) {
            IEnergyStorage dest = t.caps().energyStorage();
            SideConfig insert = t.insert();
            if (dest != null && dest != source && dest.canReceive() && insert.channel(PipeType.CH_ENERGY, pipe)) {
                destinations.add(new Dest(dest, insert));
            }
        }
        if (destinations.isEmpty()) return 0;

        int[] given = new int[destinations.size()];
        int cap = balanced ? 1 + (available - 1) / destinations.size() : Integer.MAX_VALUE;
        int remaining = available;
        for (int pass = 0; pass < (balanced ? 2 : 1) && remaining > 0; pass++) {
            for (int i = 0; i < destinations.size() && remaining > 0; i++) {
                Dest dest = destinations.get(i);
                int want = Math.min(remaining, cap - given[i]);
                // regulator: fill the target up to `limit` FE at most
                if (dest.cfg().limit > 0) want = Math.min(want, dest.cfg().limit - dest.storage().getEnergyStored());
                if (want <= 0) continue;

                int accepted = dest.storage().receiveEnergy(want, true);
                if (accepted <= 0) continue;
                int extracted = source.extractEnergy(accepted, false);
                if (extracted <= 0) return available - remaining;
                int received = dest.storage().receiveEnergy(extracted, false);
                // the target took less than it promised: give the rest back instead of losing it
                if (received < extracted) source.receiveEnergy(extracted - received, false);
                given[i] += received;
                remaining -= received;
            }
            cap = Integer.MAX_VALUE;
        }
        return available - remaining;
    }
}
