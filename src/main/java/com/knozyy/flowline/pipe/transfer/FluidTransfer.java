package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidTransfer {
    private FluidTransfer() {}

    public static void run(Level level, BlockPos sourcePos, Direction sourceAccess, SideConfig cfg,
                           java.util.List<Target> targets, int budget) {
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, sourceAccess);
        if (source == null) return;

        FluidStack offered = source.drain(budget, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return;
        if (!cfg.allows(BuiltInRegistries.FLUID.getKey(offered.getFluid()))) return;

        int remaining = budget;
        for (Target t : targets) {
            if (remaining <= 0) break;
            IFluidHandler dest = level.getCapability(Capabilities.FluidHandler.BLOCK, t.endpointPos(), t.access());
            if (dest == null || dest == source) continue;

            FluidStack request = offered.copyWithAmount(Math.min(remaining, offered.getAmount()));
            FluidStack moved = FluidUtil.tryFluidTransfer(dest, source, request, true);
            if (moved.isEmpty()) continue;

            remaining -= moved.getAmount();
            offered = source.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty() || !cfg.allows(BuiltInRegistries.FLUID.getKey(offered.getFluid()))) break;
        }
    }
}
