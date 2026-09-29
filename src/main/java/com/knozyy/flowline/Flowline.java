package com.knozyy.flowline;

import com.knozyy.flowline.registry.ModBlockEntities;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModComponents;
import com.knozyy.flowline.registry.ModCreativeTabs;
import com.knozyy.flowline.registry.ModItems;
import com.knozyy.flowline.registry.ModMenus;
import com.knozyy.flowline.registry.ModRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Flowline.MODID)
public class Flowline {
    public static final String MODID = "flowline";

    public Flowline(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, FlowlineConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, FlowlineConfig.Client.SPEC);
    }
}
