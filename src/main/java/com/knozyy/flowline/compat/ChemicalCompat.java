package com.knozyy.flowline.compat;

import com.knozyy.flowline.compat.mekanism.MekanismChemicals;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraftforge.fml.ModList;
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

    public static boolean hasHandler(BlockEntity be, Direction access) {
        return available() && MekanismChemicals.hasHandler(be, access);
    }

    /** Capability caches of one face, as an opaque object for {@link Caps}. */
    @Nullable
    public static Object cache(ServerLevel level, BlockPos pos, Direction access) {
        return available() ? MekanismChemicals.cache(level, pos, access) : null;
    }

    /** @return amount of chemicals moved */
    public static long transfer(Caps source, SideConfig cfg, List<PipeNetwork.Target> targets, long budget,
                                boolean balanced) {
        return available() ? MekanismChemicals.transfer(source, cfg, targets, budget, balanced) : 0;
    }

    public static boolean hasWork(Caps source, SideConfig cfg) {
        return available() && MekanismChemicals.hasWork(source, cfg);
    }

    /** Whether a chemical (gas, infuse type, pigment or slurry) has this id. */
    public static boolean exists(ResourceLocation id) {
        return available() && MekanismChemicals.exists(id);
    }

    public static Component name(ResourceLocation id) {
        return available() ? MekanismChemicals.name(id) : Component.literal(id.toString());
    }

    /** RGB tint of a chemical, or -1. */
    public static int tint(ResourceLocation id) {
        return available() ? MekanismChemicals.tint(id) : -1;
    }

    /** The id of the first chemical a stack holds, or null. */
    @Nullable
    public static ResourceLocation idIn(ItemStack stack) {
        return available() ? MekanismChemicals.idIn(stack) : null;
    }
}
