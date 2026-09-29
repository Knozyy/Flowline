package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

public final class FluidTransfer {
    private FluidTransfer() {}

    private record Dest(IFluidHandler handler, SideConfig cfg) {}

    /** @return millibuckets moved */
    public static int run(Level level, Caps sourceCaps, SideConfig cfg, List<Target> targets, int budget,
                          boolean balanced, PipeType pipe) {
        IFluidHandler source = sourceCaps.fluidHandler();
        if (source == null) return 0;

        List<Dest> destinations = new ArrayList<>();
        for (Target t : targets) {
            IFluidHandler h = t.caps().fluidHandler();
            SideConfig insert = t.insert();
            if (h != null && h != source && insert.channel(PipeType.CH_FLUIDS, pipe)) destinations.add(new Dest(h, insert));
        }
        if (destinations.isEmpty()) return 0;

        int[] given = new int[destinations.size()];
        int cap = Integer.MAX_VALUE;
        if (balanced) {
            // split what the source can really give this operation, not the whole budget
            int available = Math.min(budget, source.drain(budget, IFluidHandler.FluidAction.SIMULATE).getAmount());
            cap = Math.max(1, (available + destinations.size() - 1) / destinations.size());
        }
        int remaining = budget;

        for (int pass = 0; pass < (balanced ? 2 : 1) && remaining > 0; pass++) {
            for (int i = 0; i < destinations.size() && remaining > 0; i++) {
                FluidStack offered = source.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
                if (offered.isEmpty()) return budget - remaining;
                if (!cfg.allowsFluid(offered)) return budget - remaining;
                int want = Math.min(offered.getAmount(), cap - given[i]);
                if (cfg.limit > 0) {
                    // regulator: leave at least `limit` mB of this fluid in the source
                    int spare = amount(source, offered) - cfg.limit;
                    if (spare <= 0) return budget - remaining;
                    want = Math.min(want, spare);
                }
                if (want <= 0) continue;

                Dest dest = destinations.get(i);
                if (!dest.cfg().allowsFluid(offered)) continue;
                if (dest.cfg().limit > 0) {
                    want = Math.min(want, dest.cfg().limit - amount(dest.handler(), offered));
                    if (want <= 0) continue;
                }
                FluidStack moved = FluidUtil.tryFluidTransfer(dest.handler(), source, copy(offered, want), true);
                if (moved.isEmpty()) continue;
                given[i] += moved.getAmount();
                remaining -= moved.getAmount();
            }
            cap = Integer.MAX_VALUE;
        }
        return budget - remaining;
    }

    private static FluidStack copy(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    /** Millibuckets of {@code like} (same fluid and components) in all tanks of the handler. */
    static int amount(IFluidHandler handler, FluidStack like) {
        long amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isFluidEqual(like)) amount += stack.getAmount();
        }
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }
}
