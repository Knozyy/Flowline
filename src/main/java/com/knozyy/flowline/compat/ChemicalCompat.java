package com.knozyy.flowline.compat;

import com.knozyy.flowline.compat.mekanism.MekanismChemicals;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mekanism chemical support. Nothing here mentions a Mekanism type, so the class loads without Mekanism; every call
 * that needs Mekanism goes to {@link MekanismChemicals}, which is only touched when Mekanism is loaded.
 */
public final class ChemicalCompat {
    public static final String MEKANISM = "mekanism";

    private ChemicalCompat() {}

    public static boolean available() {
        return ModList.get().isLoaded(MEKANISM);
    }

    public static boolean hasHandler(Level level, BlockPos pos, Direction access) {
        return available() && MekanismChemicals.hasHandler(level, pos, access);
    }

    @Nullable
    public static BlockCapabilityCache<?, Direction> cache(ServerLevel level, BlockPos pos, Direction access) {
        return available() ? MekanismChemicals.cache(level, pos, access) : null;
    }

    /** @return amount of chemicals moved */
    public static long transfer(Caps source, List<PipeNetwork.Target> targets, long budget, boolean balanced) {
        return available() ? MekanismChemicals.transfer(source, targets, budget, balanced) : 0;
    }
}
