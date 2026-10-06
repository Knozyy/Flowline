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

    private record Dest(IFluidHandler handler, SideConfig cfg, Target target) {}

    /** @return millibuckets moved */
    public static int run(Level level, Caps sourceCaps, SideConfig cfg, List<Target> targets, int budget,
                          boolean balanced, PipeType pipe) {
        IFluidHandler source = sourceCaps.fluidHandler();
        if (source == null) return 0;

        List<Dest> destinations = new ArrayList<>();
        for (Target t : targets) {
            IFluidHandler h = t.caps().fluidHandler();
            SideConfig insert = t.insert();
            if (h != null && h != source && insert.channel(PipeType.CH_FLUIDS, pipe)) destinations.add(new Dest(h, insert, t));
        }
        if (destinations.isEmpty()) return 0;

        int[] given = new int[destinations.size()];
        int cap = Integer.MAX_VALUE;
        if (balanced) {
            // split what the source can really give this operation, not the whole budget
            int available = 0;
            List<FluidStack> seen = new ArrayList<>();
            for (int tank = 0; tank < source.getTanks() && available < budget; tank++) {
                FluidStack fluid = source.getFluidInTank(tank);
                if (fluid.isEmpty() || seen.stream().anyMatch(fluid::isFluidEqual)) continue;
                seen.add(fluid);
                available += offer(source, cfg, fluid, budget - available).getAmount();
            }
            cap = Math.max(1, 1 + (available - 1) / destinations.size());
        }
        int remaining = budget;

        for (int pass = 0; pass < (balanced ? 2 : 1) && remaining > 0; pass++) {
            for (int i = 0; i < destinations.size() && remaining > 0; i++) {
                Dest dest = destinations.get(i);
                while (remaining > 0 && given[i] < cap) {
                    FluidStack offered = offerTo(source, cfg, dest, Math.min(remaining, cap - given[i]));
                    if (offered.isEmpty()) break;
                    FluidStack moved = FluidUtil.tryFluidTransfer(dest.handler(), source, offered, true);
                    if (moved.isEmpty()) break;
                    given[i] += moved.getAmount();
                    remaining -= moved.getAmount();
                }
            }
            cap = Integer.MAX_VALUE;
        }
        return budget - remaining;
    }

    /** Search every tank: a blocked or reserved first fluid must not hide later eligible fluids. */
    private static FluidStack offerTo(IFluidHandler source, SideConfig cfg, Dest dest, int budget) {
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack fluid = source.getFluidInTank(tank);
            if (fluid.isEmpty() || !dest.cfg().allowsFluid(fluid)) continue;
            int want = budget;
            int max = dest.cfg().limitFor(fluid);
            if (max > 0) want = (int) Math.max(0, Math.min(want, max - amount(dest.handler(), fluid)));
            FluidStack offered = offer(source, cfg, fluid, want);
            if (!offered.isEmpty() && dest.handler().fill(offered, IFluidHandler.FluidAction.SIMULATE) > 0) return offered;
        }
        return FluidStack.EMPTY;
    }

    private static FluidStack offer(IFluidHandler source, SideConfig cfg, FluidStack fluid, int budget) {
        if (budget <= 0 || !cfg.allowsFluid(fluid)) return FluidStack.EMPTY;
        int keep = cfg.limitFor(fluid);
        if (keep > 0) budget = (int) Math.max(0, Math.min(budget, amount(source, fluid) - keep));
        return budget <= 0 ? FluidStack.EMPTY : source.drain(copy(fluid, budget), IFluidHandler.FluidAction.SIMULATE);
    }

    private static FluidStack copy(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    public static boolean hasWork(IFluidHandler handler, SideConfig cfg) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (!fluid.isEmpty() && !offer(handler, cfg, fluid, 1).isEmpty()) return true;
        }
        return false;
    }

    /** Millibuckets of {@code like} (same fluid and components) in all tanks of the handler. */
    static long amount(IFluidHandler handler, FluidStack like) {
        long amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isFluidEqual(like)) amount += stack.getAmount();
        }
        return amount;
    }
}
