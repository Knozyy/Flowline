package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

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
    public boolean whitelist = false;
    /** Also compare data components (NBT): enchantments, damage, custom names, fluid data... */
    public boolean matchComponents = false;
    /**
     * Filter samples by position, one item each; empty stacks are holes. Item pipes compare the item itself; fluid
     * pipes compare the fluid contained in the sample (a bucket or any other fluid container). Only the first
     * {@link #filterCapacity()} positions are active, so entries past it survive removing a Filter upgrade.
     */
    public final List<ItemStack> filter = new ArrayList<>();
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

    /** Forget pacing and cached targets, e.g. when the side stops extracting. */
    public void resetRuntime() {
        interval = -1;
        cooldown = 0;
        sleeping = false;
        cachedTargets = null;
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

    public ItemStack getSample(int index) {
        return index < filter.size() ? filter.get(index) : ItemStack.EMPTY;
    }

    public void setSample(int index, ItemStack sample) {
        while (filter.size() <= index) filter.add(ItemStack.EMPTY);
        filter.set(index, sample);
    }

    /** The non-empty samples within the current capacity. */
    private List<ItemStack> activeSamples() {
        List<ItemStack> active = new ArrayList<>();
        int end = Math.min(filter.size(), filterCapacity());
        for (int i = 0; i < end; i++) {
            if (!filter.get(i).isEmpty()) active.add(filter.get(i));
        }
        return active;
    }

    /** Filters without active entries allow everything, in both whitelist and blacklist mode. */
    public boolean allowsItem(ItemStack stack) {
        List<ItemStack> samples = activeSamples();
        if (samples.isEmpty()) return true;
        boolean listed = false;
        for (ItemStack sample : samples) {
            if (matchComponents ? ItemStack.isSameItemSameComponents(sample, stack) : ItemStack.isSameItem(sample, stack)) {
                listed = true;
                break;
            }
        }
        return whitelist == listed;
    }

    public boolean allowsFluid(FluidStack fluid) {
        List<ItemStack> samples = activeSamples();
        if (samples.isEmpty()) return true;
        boolean listed = false;
        for (ItemStack sample : samples) {
            FluidStack sampleFluid = fluidOf(sample);
            if (sampleFluid.isEmpty()) continue;
            if (matchComponents ? FluidStack.isSameFluidSameComponents(sampleFluid, fluid)
                    : FluidStack.isSameFluid(sampleFluid, fluid)) {
                listed = true;
                break;
            }
        }
        return whitelist == listed;
    }

    public static FluidStack fluidOf(ItemStack container) {
        return FluidUtil.getFluidContained(container).orElse(FluidStack.EMPTY);
    }

    /** Whether {@code stack} can be used as a filter sample for pipes of {@code type}. */
    public static boolean isValidSample(PipeType type, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (type) {
            case ITEM -> true;
            case FLUID -> !fluidOf(stack).isEmpty();
            case ENERGY -> false;
        };
    }

    // ---- persistence --------------------------------------------------------------------------------------

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("distribution", distribution.name());
        tag.putString("redstone", redstone.name());
        tag.putBoolean("whitelist", whitelist);
        tag.putBoolean("match_components", matchComponents);
        ListTag list = new ListTag();
        for (int i = 0; i < filter.size(); i++) {
            if (filter.get(i).isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.put("item", filter.get(i).save(registries));
            list.add(entry);
        }
        tag.put("filter", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        mode = SideMode.byName(tag.getString("mode"));
        distribution = Distribution.byName(tag.getString("distribution"));
        redstone = RedstoneMode.byName(tag.getString("redstone"));
        whitelist = tag.getBoolean("whitelist");
        matchComponents = tag.getBoolean("match_components");
        filter.clear();
        for (Tag t : tag.getList("filter", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            int slot = entry.getInt("slot");
            ItemStack.parse(registries, entry.getCompound("item"))
                    .filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> setSample(slot, stack));
        }
    }
}
