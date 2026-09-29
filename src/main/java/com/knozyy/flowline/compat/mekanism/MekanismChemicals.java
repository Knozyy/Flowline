package com.knozyy.flowline.compat.mekanism;

import com.knozyy.flowline.pipe.CapCache;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism's four chemical capabilities (gases, infuse types, pigments, slurries). Forge hands out capabilities by
 * interface, so these are the very instances Mekanism registers. Only loaded when Mekanism is present.
 */
public final class MekanismChemicals {
    public static final Capability<IGasHandler> GAS = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IInfusionHandler> INFUSION = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IPigmentHandler> PIGMENT = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ISlurryHandler> SLURRY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final List<Capability<? extends IChemicalHandler<?, ?>>> ALL = List.of(GAS, INFUSION, PIGMENT, SLURRY);

    private MekanismChemicals() {}

    public static boolean hasHandler(BlockEntity be, Direction access) {
        for (Capability<? extends IChemicalHandler<?, ?>> cap : ALL) {
            if (be.getCapability(cap, access).isPresent()) return true;
        }
        return false;
    }

    public static Object cache(ServerLevel level, BlockPos pos, Direction access) {
        List<CapCache<? extends IChemicalHandler<?, ?>>> caches = new ArrayList<>();
        for (Capability<? extends IChemicalHandler<?, ?>> cap : ALL) caches.add(new CapCache<>(cap, level, pos, access));
        return caches;
    }

    @SuppressWarnings("unchecked")
    private static IChemicalHandler<?, ?> handler(Object caches, int kind) {
        if (!(caches instanceof List<?> list) || kind >= list.size()) return null;
        return ((CapCache<? extends IChemicalHandler<?, ?>>) list.get(kind)).get();
    }

    public static long transfer(Caps sourceCaps, List<PipeNetwork.Target> targets, long budget, boolean balanced) {
        long moved = 0;
        for (int kind = 0; kind < ALL.size(); kind++) {
            IChemicalHandler<?, ?> source = handler(sourceCaps.chemicals(), kind);
            if (source == null) continue;
            List<IChemicalHandler<?, ?>> destinations = new ArrayList<>();
            for (PipeNetwork.Target t : targets) {
                IChemicalHandler<?, ?> h = handler(t.caps().chemicals(), kind);
                if (h != null && h != source) destinations.add(h);
            }
            if (!destinations.isEmpty()) moved += move(source, destinations, budget - moved, balanced);
            if (moved >= budget) break;
        }
        return moved;
    }

    /** One kind: raw types because the four handler interfaces only share the generic parent. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long move(IChemicalHandler source, List<IChemicalHandler<?, ?>> destinations, long budget,
                             boolean balanced) {
        long remaining = budget;
        long cap = balanced ? Math.max(1, (budget + destinations.size() - 1) / destinations.size()) : Long.MAX_VALUE;
        for (int pass = 0; pass < (balanced ? 2 : 1) && remaining > 0; pass++) {
            for (IChemicalHandler dest : destinations) {
                if (remaining <= 0) break;
                ChemicalStack offered = source.extractChemical(Math.min(remaining, cap), Action.SIMULATE);
                if (offered.isEmpty()) return budget - remaining;
                ChemicalStack rest = dest.insertChemical(offered, Action.SIMULATE);
                long accepted = offered.getAmount() - rest.getAmount();
                if (accepted <= 0) continue;
                ChemicalStack extracted = source.extractChemical(accepted, Action.EXECUTE);
                if (extracted.isEmpty()) continue;
                ChemicalStack left = dest.insertChemical(extracted, Action.EXECUTE);
                // the target took less than it promised: give the rest back
                if (!left.isEmpty()) source.insertChemical(left, Action.EXECUTE);
                remaining -= extracted.getAmount() - left.getAmount();
            }
            cap = Long.MAX_VALUE;
        }
        return budget - remaining;
    }
}
