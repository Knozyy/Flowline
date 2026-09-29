package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Flowline.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flowline"))
                    .icon(() -> new ItemStack(ModItems.ITEM_PIPE.get()))
                    .displayItems((params, out) -> {
                        out.accept(ModItems.ITEM_PIPE.get());
                        out.accept(ModItems.FLUID_PIPE.get());
                        out.accept(ModItems.ENERGY_PIPE.get());
                        out.accept(ModItems.UNIVERSAL_PIPE.get());
                        if (ModItems.CHEMICAL_PIPE != null) out.accept(ModItems.CHEMICAL_PIPE.get());
                        out.accept(ModItems.WRENCH.get());
                        ModItems.UPGRADES.forEach(u -> out.accept(u.get()));
                        out.accept(ModItems.CONFIG_CARD.get());
                        out.accept(ModItems.FILTER_CARD.get());
                        out.accept(ModItems.FACADE.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
