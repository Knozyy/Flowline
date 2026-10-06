package com.knozyy.flowline.compat;

import com.knozyy.flowline.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Short how-to pages shown by JEI and EMI on Flowline's items. The text lives in the lang files as
 * {@code guide.flowline.<id>.<n>}, one paragraph per key.
 */
public final class GuidePages {
    public record Page(String id, List<ItemStack> items, List<Component> lines) {}

    private GuidePages() {}

    public static List<Page> pages() {
        List<ItemStack> pipes = new ArrayList<>(List.of(stack(ModItems.ITEM_PIPE), stack(ModItems.FLUID_PIPE),
                stack(ModItems.ENERGY_PIPE)));
        if (ModItems.CHEMICAL_PIPE != null) pipes.add(stack(ModItems.CHEMICAL_PIPE));
        return List.of(
                page("pipes", pipes, 4),
                page("universal_pipe", List.of(stack(ModItems.UNIVERSAL_PIPE)), 2),
                page("wrench", List.of(stack(ModItems.WRENCH)), 4),
                page("upgrades", ModItems.UPGRADES.stream().map(GuidePages::stack).toList(), 2),
                page("filter_card", List.of(stack(ModItems.FILTER_CARD)), 2));
    }

    private static Page page(String id, List<ItemStack> items, int paragraphs) {
        List<Component> lines = new ArrayList<>();
        for (int i = 1; i <= paragraphs; i++) lines.add(Component.translatable("guide.flowline." + id + "." + i));
        return new Page(id, items, lines);
    }

    private static ItemStack stack(RegistryObject<? extends net.minecraft.world.item.Item> item) {
        return new ItemStack(item.get());
    }
}
