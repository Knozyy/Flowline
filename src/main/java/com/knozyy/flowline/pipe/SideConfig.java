package com.knozyy.flowline.pipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Per-side configuration of a pipe block entity. */
public class SideConfig {
    public SideMode mode = SideMode.INSERT;
    public Distribution distribution = Distribution.NEAREST;
    public SpeedTier speed = SpeedTier.BASE;
    public boolean whitelist = false;
    public final List<ResourceLocation> filter = new ArrayList<>();
    /** Rotating cursor for {@link Distribution#ROUND_ROBIN}. Not persisted. */
    public int roundRobin = 0;

    /** Empty filters allow everything, in both whitelist and blacklist mode. */
    public boolean allows(ResourceLocation id) {
        if (filter.isEmpty()) return true;
        return whitelist == filter.contains(id);
    }

    /** @return true if the id is now part of the filter, false if it was removed. */
    public boolean toggleFilter(ResourceLocation id) {
        if (filter.remove(id)) return false;
        filter.add(id);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("distribution", distribution.name());
        tag.putInt("speed", speed.ordinal());
        tag.putBoolean("whitelist", whitelist);
        ListTag list = new ListTag();
        for (ResourceLocation id : filter) list.add(StringTag.valueOf(id.toString()));
        tag.put("filter", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        mode = SideMode.byName(tag.getString("mode"));
        distribution = Distribution.byName(tag.getString("distribution"));
        speed = SpeedTier.byIndex(tag.getInt("speed"));
        whitelist = tag.getBoolean("whitelist");
        filter.clear();
        for (Tag t : tag.getList("filter", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) filter.add(id);
        }
    }

    public static ResourceLocation itemId(net.minecraft.world.item.Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
