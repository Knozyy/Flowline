package com.knozyy.flowline.compat.mekanism;

import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.ArrayList;
import java.util.List;

/** Mekanism's chemical handler capability. Only loaded when Mekanism is present (see ChemicalCompat). */
public final class MekanismChemicals {
    /** Same name and type as Mekanism's own block capability, so this returns that very instance. */
    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private MekanismChemicals() {}

    public static boolean hasHandler(Level level, BlockPos pos, Direction access) {
        return level.getCapability(CHEMICAL, pos, access) != null;
    }

    public static BlockCapabilityCache<?, Direction> cache(ServerLevel level, BlockPos pos, Direction access) {
        return BlockCapabilityCache.create(CHEMICAL, level, pos, access);
    }

    public static long transfer(Caps sourceCaps, List<PipeNetwork.Target> targets, long budget, boolean balanced) {
        if (!(sourceCaps.chemicalHandler() instanceof IChemicalHandler source)) return 0;
        List<IChemicalHandler> destinations = new ArrayList<>();
        for (PipeNetwork.Target t : targets) {
            if (t.caps().chemicalHandler() instanceof IChemicalHandler h && h != source) destinations.add(h);
        }
        if (destinations.isEmpty()) return 0;

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
                ChemicalStack extracted = source.extractChemical(offered.copyWithAmount(accepted), Action.EXECUTE);
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
