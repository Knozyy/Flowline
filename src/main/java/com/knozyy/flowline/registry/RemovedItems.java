package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Items removed in 0.3, so worlds saved with them open without Forge's missing-entries warning. A Configuration Card
 * becomes a Filter Card (both keep their rules under the same NBT key, so its filter survives); a facade is dropped.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class RemovedItems {
    private static final Set<String> DROPPED = Set.of("facade");

    private RemovedItems() {}

    /** The item that now stands for removed item {@code id}, or null. */
    @Nullable
    public static Item replacement(ResourceLocation id) {
        if (!Flowline.MODID.equals(id.getNamespace())) return null;
        return id.getPath().equals("config_card") ? ModItems.FILTER_CARD.get() : null;
    }

    /** Whether removed item {@code id} is simply gone. */
    public static boolean dropped(ResourceLocation id) {
        return Flowline.MODID.equals(id.getNamespace()) && DROPPED.contains(id.getPath());
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Flowline.MODID)) {
            Item item = replacement(mapping.getKey());
            if (item != null) mapping.remap(item);
            else if (dropped(mapping.getKey())) mapping.ignore();
        }
    }
}
