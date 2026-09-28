package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidTransfer {
    private FluidTransfer() {}

    /** @return millibuckets moved. */
    public static int run(Level level, BlockCapabilityCache<?, Direction> sourceCache, SideConfig cfg,
                          java.util.List<Target> targets, int budget) {
        if (!(sourceCache.getCapability() instanceof IFluidHandler source)) return 0;

        FluidStack offered = source.drain(budget, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return 0;
        if (!cfg.allowsFluid(offered, level.registryAccess())) return 0;

        int remaining = budget;
        for (Target t : targets) {
            if (remaining <= 0) break;
            if (!(t.cache().getCapability() instanceof IFluidHandler dest) || dest == source) continue;

            FluidStack request = offered.copyWithAmount(Math.min(remaining, offered.getAmount()));
            FluidStack moved = FluidUtil.tryFluidTransfer(dest, source, request, true);
            if (moved.isEmpty()) continue;

            remaining -= moved.getAmount();
            offered = source.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty() || !cfg.allowsFluid(offered, level.registryAccess())) break;
        }
        return budget - remaining;
    }
}
