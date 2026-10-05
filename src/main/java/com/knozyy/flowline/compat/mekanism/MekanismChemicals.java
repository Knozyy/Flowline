package com.knozyy.flowline.compat.mekanism;

import com.knozyy.flowline.pipe.CapCache;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.ArrayList;
import java.util.List;
import com.knozyy.flowline.pipe.SideConfig;
import org.jetbrains.annotations.Nullable;

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

    // ---- identity (for filter rules) ----------------------------------------------------------------------

    /** The chemical registries in the order of {@link #ALL}. */
    private static List<net.minecraftforge.registries.IForgeRegistry<? extends mekanism.api.chemical.Chemical<?>>> registries() {
        return List.of(MekanismAPI.gasRegistry(), MekanismAPI.infuseTypeRegistry(), MekanismAPI.pigmentRegistry(),
                MekanismAPI.slurryRegistry());
    }

    /** The chemical with this id in any of the four registries, or null (the empty chemical counts as none). */
    @Nullable
    public static mekanism.api.chemical.Chemical<?> chemical(ResourceLocation id) {
        for (net.minecraftforge.registries.IForgeRegistry<? extends mekanism.api.chemical.Chemical<?>> registry : registries()) {
            if (registry.containsKey(id)) {
                mekanism.api.chemical.Chemical<?> chemical = registry.getValue(id);
                if (chemical != null && !chemical.isEmptyType()) return chemical;
            }
        }
        return null;
    }

    public static boolean exists(ResourceLocation id) {
        return chemical(id) != null;
    }

    public static Component name(ResourceLocation id) {
        mekanism.api.chemical.Chemical<?> chemical = chemical(id);
        return chemical == null ? Component.literal(id.toString()) : chemical.getTextComponent();
    }

    /** ARGB-less RGB tint of the chemical, or -1. */
    public static int tint(ResourceLocation id) {
        mekanism.api.chemical.Chemical<?> chemical = chemical(id);
        return chemical == null ? -1 : chemical.getTint() & 0xFFFFFF;
    }

    /** The registry id of the first chemical an item holds (Mekanism tanks, canisters...), or null. */
    @Nullable
    public static ResourceLocation idIn(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Capability<? extends IChemicalHandler<?, ?>> cap : ALL) {
            IChemicalHandler<?, ?> handler = stack.getCapability(cap).resolve().orElse(null);
            if (handler == null) continue;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                ChemicalStack<?> held = handler.getChemicalInTank(tank);
                if (!held.isEmpty()) return held.getTypeRegistryName();
            }
        }
        return null;
    }

    // ---- transfer -----------------------------------------------------------------------------------------

    public static long transfer(Caps sourceCaps, SideConfig sourceCfg, List<PipeNetwork.Target> targets, long budget,
                                boolean balanced) {
        long moved = 0;
        for (int kind = 0; kind < ALL.size(); kind++) {
            IChemicalHandler<?, ?> source = handler(sourceCaps.chemicals(), kind);
            if (source == null) continue;
            List<Dest> destinations = new ArrayList<>();
            for (PipeNetwork.Target t : targets) {
                IChemicalHandler<?, ?> h = handler(t.caps().chemicals(), kind);
                if (h != null && h != source) destinations.add(new Dest(h, t.insert()));
            }
            if (!destinations.isEmpty()) moved += move(source, sourceCfg, destinations, budget - moved, balanced);
            if (moved >= budget) break;
        }
        return moved;
    }

    private record Dest(IChemicalHandler<?, ?> handler, SideConfig cfg) {}

    /** Whether the source side and the target side both let {@code stack}'s chemical through. */
    private static boolean allowed(SideConfig source, SideConfig target, ChemicalStack<?> stack) {
        ResourceLocation id = stack.getTypeRegistryName();
        return source.allowsChemical(id, () -> stack.getTextComponent().getString())
                && target.allowsChemical(id, () -> stack.getTextComponent().getString());
    }

    /** One kind: raw types because the four handler interfaces only share the generic parent. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long move(IChemicalHandler source, SideConfig sourceCfg, List<Dest> destinations, long budget,
                             boolean balanced) {
        long remaining = budget;
        long cap = balanced ? Math.max(1, (budget + destinations.size() - 1) / destinations.size()) : Long.MAX_VALUE;
        for (int pass = 0; pass < (balanced ? 2 : 1) && remaining > 0; pass++) {
            for (Dest d : destinations) {
                IChemicalHandler dest = d.handler();
                if (remaining <= 0) break;
                long want = Math.min(remaining, cap);
                // search every tank: a blocked first chemical must not hide later eligible ones
                for (int tank = 0; tank < source.getTanks() && want > 0; tank++) {
                    ChemicalStack held = source.getChemicalInTank(tank);
                    if (held.isEmpty() || !allowed(sourceCfg, d.cfg(), held)) continue;
                    ChemicalStack offered = source.extractChemical(tank, want, Action.SIMULATE);
                    if (offered.isEmpty()) continue;
                    ChemicalStack rest = dest.insertChemical(offered, Action.SIMULATE);
                    long accepted = offered.getAmount() - rest.getAmount();
                    if (accepted <= 0) continue;
                    ChemicalStack extracted = source.extractChemical(tank, accepted, Action.EXECUTE);
                    if (extracted.isEmpty()) continue;
                    ChemicalStack left = dest.insertChemical(extracted, Action.EXECUTE);
                    // the target took less than it promised: give the rest back
                    if (!left.isEmpty()) source.insertChemical(left, Action.EXECUTE);
                    long done = extracted.getAmount() - left.getAmount();
                    remaining -= done;
                    want -= done;
                }
            }
            cap = Long.MAX_VALUE;
        }
        return budget - remaining;
    }

    /** Whether the source holds a chemical this side may take (for the "stuck" redstone output). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean hasWork(Caps sourceCaps, SideConfig cfg) {
        for (int kind = 0; kind < ALL.size(); kind++) {
            IChemicalHandler source = handler(sourceCaps.chemicals(), kind);
            if (source == null) continue;
            for (int tank = 0; tank < source.getTanks(); tank++) {
                ChemicalStack held = source.getChemicalInTank(tank);
                if (!held.isEmpty() && cfg.allowsChemical(held.getTypeRegistryName(),
                        () -> held.getTextComponent().getString())) return true;
            }
        }
        return false;
    }
}
