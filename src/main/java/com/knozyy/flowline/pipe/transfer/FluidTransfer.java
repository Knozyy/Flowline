package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.knozyy.flowline.pipe.Caps;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidTransfer {
    private FluidTransfer() {}

    /**
     * @param applyFilter false on pipes whose filter is about items (universal pipes)
     * @return millibuckets moved
     */
    public static int run(Level level, Caps sourceCaps, SideConfig cfg, java.util.List<Target> targets, int budget,
                          boolean applyFilter) {
        IFluidHandler source = sourceCaps.fluidHandler();
        if (source == null) return 0;

        FluidStack offered = source.drain(budget, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return 0;
        if (applyFilter && !cfg.allowsFluid(offered, level.registryAccess())) return 0;

        int remaining = budget;
        for (Target t : targets) {
            if (remaining <= 0) break;
            IFluidHandler dest = t.caps().fluidHandler();
            if (dest == null || dest == source) continue;

            FluidStack request = offered.copyWithAmount(Math.min(remaining, offered.getAmount()));
            FluidStack moved = FluidUtil.tryFluidTransfer(dest, source, request, true);
            if (moved.isEmpty()) continue;

            remaining -= moved.getAmount();
            offered = source.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty() || applyFilter && !cfg.allowsFluid(offered, level.registryAccess())) break;
        }
        return budget - remaining;
    }
}
