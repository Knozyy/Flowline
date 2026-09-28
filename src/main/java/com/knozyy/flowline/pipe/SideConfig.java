package com.knozyy.flowline.pipe;

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
    /** Size of the filter; matches the number of ghost slots in the configuration GUI. */
    public static final int MAX_FILTER = 9;

    public SideMode mode = SideMode.INSERT;
    public Distribution distribution = Distribution.NEAREST;
    public SpeedTier speed = SpeedTier.BASE;
    public RedstoneMode redstone = RedstoneMode.IGNORED;
    public boolean whitelist = false;
    /** Also compare data components (NBT): enchantments, damage, custom names, fluid data... */
    public boolean matchComponents = false;
    /**
     * Filter samples, one item each. Item pipes compare the item itself; fluid pipes compare the fluid contained
     * in the sample (a bucket or any other fluid container).
     */
    public final List<ItemStack> filter = new ArrayList<>();
    /** Rotating cursor for {@link Distribution#ROUND_ROBIN}. Not persisted. */
    public int roundRobin = 0;

    // ---- matching -----------------------------------------------------------------------------------------

    /** Empty filters allow everything, in both whitelist and blacklist mode. */
    public boolean allowsItem(ItemStack stack) {
        if (filter.isEmpty()) return true;
        boolean listed = false;
        for (ItemStack sample : filter) {
            if (matchComponents ? ItemStack.isSameItemSameComponents(sample, stack) : ItemStack.isSameItem(sample, stack)) {
                listed = true;
                break;
            }
        }
        return whitelist == listed;
    }

    public boolean allowsFluid(FluidStack fluid) {
        if (filter.isEmpty()) return true;
        boolean listed = false;
        for (ItemStack sample : filter) {
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

    // ---- editing ------------------------------------------------------------------------------------------

    public boolean isFilterFull() {
        return filter.size() >= MAX_FILTER;
    }

    public int indexOfSample(ItemStack stack) {
        for (int i = 0; i < filter.size(); i++) {
            if (ItemStack.isSameItemSameComponents(filter.get(i), stack)) return i;
        }
        return -1;
    }

    // ---- persistence --------------------------------------------------------------------------------------

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("distribution", distribution.name());
        tag.putInt("speed", speed.ordinal());
        tag.putString("redstone", redstone.name());
        tag.putBoolean("whitelist", whitelist);
        tag.putBoolean("match_components", matchComponents);
        ListTag list = new ListTag();
        for (ItemStack sample : filter) {
            if (!sample.isEmpty()) list.add(sample.save(registries));
        }
        tag.put("filter", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        mode = SideMode.byName(tag.getString("mode"));
        distribution = Distribution.byName(tag.getString("distribution"));
        speed = SpeedTier.byIndex(tag.getInt("speed"));
        redstone = RedstoneMode.byName(tag.getString("redstone"));
        whitelist = tag.getBoolean("whitelist");
        matchComponents = tag.getBoolean("match_components");
        filter.clear();
        for (Tag t : tag.getList("filter", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).filter(s -> !s.isEmpty()).ifPresent(filter::add);
        }
    }
}
