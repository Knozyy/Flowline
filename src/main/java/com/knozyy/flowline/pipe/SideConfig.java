package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.filter.CompiledFilter;
import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Per-side configuration of a pipe block entity. */
public class SideConfig {
    /** Filter entries shown per page in the configuration GUI (a 3x3 grid). */
    public static final int FILTER_PAGE = 9;
    /** Upgrade slots per side. */
    public static final int UPGRADE_SLOTS = 6;
    /** Bounds of {@link #priority}. */
    public static final int MAX_PRIORITY = 999;
    /** Upper bound of {@link #limit} and {@link #rate}. */
    public static final int MAX_AMOUNT = 1_000_000_000;

    public final PipeType type;
    public SideMode mode = SideMode.INSERT;
    public Distribution distribution = Distribution.NEAREST;
    public RedstoneMode redstone = RedstoneMode.IGNORED;
    /** Insert sides: extracting sides using {@link Distribution#PRIORITY} fill higher priorities first. */
    public int priority = 0;
    /**
     * Regulator, 0 = off. Insert sides: keep at most this much of each kind in the target. Extract sides: leave at
     * least this much of each kind in the source. Items count items, fluids millibuckets, energy FE.
     */
    public int limit = 0;
    /** Extract sides moving energy: at most this many FE per tick, 0 = unlimited. */
    public int rate = 0;
    /** Universal pipes: which kinds this side moves ({@link PipeType#CH_ITEMS} ...). */
    public int channels = PipeType.ALL_CHANNELS;
    /**
     * Filter rules by position; null entries are holes. Only the first {@link #filterCapacity()} positions are
     * active, so rules past it survive removing a Filter upgrade. Change them through {@link #setEntry}.
     */
    private final List<FilterEntry> filter = new ArrayList<>();
    /** Resolved form of the active rules; rebuilt lazily after edits or capacity changes. */
    private CompiledFilter compiled = null;
    private int compiledCapacity = -1;
    /** Rotating cursor for {@link Distribution#ROUND_ROBIN} and {@link Distribution#BALANCED}. Not persisted. */
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
    /** No target in the network: skip work until {@link #graph} is invalidated. */
    public boolean sleeping = false;
    /** Pulse mode: a rising redstone edge arrived and one operation is due. */
    public boolean pulsePending = false;
    /** The pipe network this side extracts into; {@link PipeNetwork.Graph#valid()} turns false when it changes. */
    @Nullable
    public PipeNetwork.Graph graph = null;
    /** Insert sides reachable from this side, in base order; taken from {@link #graph}. */
    public List<PipeNetwork.Target> cachedTargets = null;
    /** Capability cache of the block this side extracts from; NeoForge invalidates it when that block changes. */
    public Caps sourceCaps = null;

    public SideConfig(PipeType type) {
        this.type = type;
    }

    /** Forget pacing and cached targets, e.g. when the side stops extracting. */
    public void resetRuntime() {
        interval = -1;
        cooldown = 0;
        sleeping = false;
        pulsePending = false;
        graph = null;
        cachedTargets = null;
        sourceCaps = null;
    }

    /** Leave sleep and run again soon, at the starting interval at the latest. */
    public void wake() {
        sleeping = false;
        if (interval < 0) return;
        int start = Pacing.start(speedCount);
        if (interval > start) interval = start;
        if (cooldown > interval) cooldown = interval;
    }

    /** Whether this side moves the kind {@code bit}; always true on pipes without channels. */
    public boolean channel(int bit, PipeType pipe) {
        return !pipe.hasChannels() || (channels & bit) != 0;
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

    /** Positions of the stored rules, including those past the capacity. */
    public int filterSize() {
        return filter.size();
    }

    public boolean hasRules() {
        return filter.stream().anyMatch(e -> e != null && !e.isEmpty());
    }

    private CompiledFilter compiled() {
        int capacity = filterCapacity();
        if (compiled == null || compiledCapacity != capacity) {
            compiled = CompiledFilter.compile(filter.subList(0, Math.min(filter.size(), capacity)), type);
            compiledCapacity = capacity;
        }
        return compiled;
    }

    public boolean allowsItem(ItemStack stack) {
        return compiled().allowsItem(stack);
    }

    public boolean allowsFluid(FluidStack fluid) {
        return compiled().allowsFluid(fluid);
    }

    // ---- persistence --------------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = saveSettings();
        tag.putString("mode", mode.name());
        tag.put("filter", saveFilter());
        return tag;
    }

    /** Everything a Configuration Card copies except the mode and the filter. */
    public CompoundTag saveSettings() {
        CompoundTag tag = new CompoundTag();
        tag.putString("distribution", distribution.name());
        tag.putString("redstone", redstone.name());
        if (priority != 0) tag.putInt("priority", priority);
        if (limit != 0) tag.putInt("limit", limit);
        if (rate != 0) tag.putInt("rate", rate);
        if (channels != PipeType.ALL_CHANNELS) tag.putInt("channels", channels);
        return tag;
    }

    public ListTag saveFilter() {
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
        return list;
    }

    public void load(CompoundTag tag) {
        mode = SideMode.byName(tag.getString("mode"));
        loadSettings(tag);
        loadFilter(tag.getList("filter", Tag.TAG_COMPOUND));
    }

    public void loadSettings(CompoundTag tag) {
        distribution = Distribution.byName(tag.getString("distribution"));
        redstone = RedstoneMode.byName(tag.getString("redstone"));
        priority = Math.max(-MAX_PRIORITY, Math.min(MAX_PRIORITY, tag.getInt("priority")));
        limit = Math.max(0, Math.min(MAX_AMOUNT, tag.getInt("limit")));
        rate = Math.max(0, Math.min(MAX_AMOUNT, tag.getInt("rate")));
        channels = tag.contains("channels") ? tag.getInt("channels") & PipeType.ALL_CHANNELS : PipeType.ALL_CHANNELS;
    }

    public void loadFilter(ListTag list) {
        clearFilter();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            int slot = entry.getInt("slot");
            if (slot < 0 || slot > 1024) continue;
            FilterEntry.CODEC.parse(NbtOps.INSTANCE, entry.get("rule")).result()
                    .ifPresent(rule -> setEntry(slot, rule));
        }
    }
}
