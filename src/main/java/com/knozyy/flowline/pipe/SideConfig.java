package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.filter.CompiledFilter;
import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Per-side configuration of a pipe block entity. */
public class SideConfig {
    /** Filter entries shown per page in the configuration GUI (a 3x3 grid). */
    public static final int FILTER_PAGE = 9;
    /** Upgrade slots per side. */
    public static final int UPGRADE_SLOTS = 6;

    public SideMode mode = SideMode.INSERT;
    public Distribution distribution = Distribution.NEAREST;
    public RedstoneMode redstone = RedstoneMode.IGNORED;
    /**
     * Filter rules by position; null entries are holes. Only the first {@link #filterCapacity()} positions are
     * active, so rules past it survive removing a Filter upgrade. Change them through {@link #setEntry}.
     */
    private final List<FilterEntry> filter = new ArrayList<>();
    /** Resolved form of the active rules; rebuilt lazily after edits or capacity changes. */
    private CompiledFilter compiled = null;
    private int compiledCapacity = -1;
    /** Rotating cursor for {@link Distribution#ROUND_ROBIN}. Not persisted. */
    public int roundRobin = 0;

    // ---- runtime state, derived or reset on load, never saved ---------------------------------------------

    /** Speed, Stack and Filter contributions of the installed upgrades; kept in sync by the block entity. */
    public int speedCount = 0;
    public int stackCount = 0;
    public int filterCount = 0;
    /** Current ticks between operations; -1 until the side runs for the first time. See {@link Pacing}. */
    public int interval = -1;
    /** Ticks left until the next operation. */
    public int cooldown = 0;
    /** No target in the network: skip work until {@link PipeNetwork#version()} changes. */
    public boolean sleeping = false;
    public long sleepVersion = -1;
    /** Insert sides reachable from this side, in base order; rebuilt when the network version changes. */
    public List<PipeNetwork.Target> cachedTargets = null;
    public long cachedVersion = -1;
    /** Capability cache of the block this side extracts from; NeoForge invalidates it when that block changes. */
    public BlockCapabilityCache<?, Direction> sourceCache = null;

    /** Forget pacing and cached targets, e.g. when the side stops extracting. */
    public void resetRuntime() {
        interval = -1;
        cooldown = 0;
        sleeping = false;
        cachedTargets = null;
        sourceCache = null;
    }

    /** Leave sleep and run again soon, at the starting interval at the latest. */
    public void wake() {
        sleeping = false;
        if (interval < 0) return;
        int start = Pacing.start(speedCount);
        if (interval > start) interval = start;
        if (cooldown > interval) cooldown = interval;
    }

    // ---- matching -----------------------------------------------------------------------------------------

    /** Number of usable filter entries for {@code filterCount} Filter upgrades. */
    public static int filterCapacity(int filterCount) {
        return FlowlineConfig.BASE_FILTER_SLOTS.get() + filterCount * FlowlineConfig.FILTER_SLOTS_PER_UPGRADE.get();
    }

    public int filterCapacity() {
        return filterCapacity(filterCount);
    }

    @Nullable
    public FilterEntry getEntry(int index) {
        return index < filter.size() ? filter.get(index) : null;
    }

    public void setEntry(int index, @Nullable FilterEntry entry) {
        while (filter.size() <= index) filter.add(null);
        filter.set(index, entry);
        compiled = null;
    }

    public void clearFilter() {
        filter.clear();
        compiled = null;
    }

    private CompiledFilter compiled() {
        int capacity = filterCapacity();
        if (compiled == null || compiledCapacity != capacity) {
            compiled = CompiledFilter.compile(filter.subList(0, Math.min(filter.size(), capacity)));
            compiledCapacity = capacity;
        }
        return compiled;
    }

    public boolean allowsItem(ItemStack stack, HolderLookup.Provider registries) {
        return compiled().allowsItem(stack, registries);
    }

    public boolean allowsFluid(FluidStack fluid, HolderLookup.Provider registries) {
        return compiled().allowsFluid(fluid, registries);
    }

    // ---- persistence --------------------------------------------------------------------------------------

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("distribution", distribution.name());
        tag.putString("redstone", redstone.name());
        ListTag list = new ListTag();
        for (int i = 0; i < filter.size(); i++) {
            FilterEntry rule = filter.get(i);
            if (rule == null) continue;
            int slot = i;
            FilterEntry.CODEC.encodeStart(NbtOps.INSTANCE, rule).result().ifPresent(encoded -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.put("rule", encoded);
                list.add(entry);
            });
        }
        tag.put("filter", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        mode = SideMode.byName(tag.getString("mode"));
        distribution = Distribution.byName(tag.getString("distribution"));
        redstone = RedstoneMode.byName(tag.getString("redstone"));
        clearFilter();
        for (Tag t : tag.getList("filter", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            int slot = entry.getInt("slot");
            FilterEntry.CODEC.parse(NbtOps.INSTANCE, entry.get("rule")).result()
                    .ifPresent(rule -> setEntry(slot, rule));
        }
    }
}
