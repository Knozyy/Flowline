package com.knozyy.flowline;

import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.registry.ModBlockEntities;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModCreativeTabs;
import com.knozyy.flowline.registry.ModItems;
import com.knozyy.flowline.registry.ModMenus;
import com.knozyy.flowline.registry.ModRecipes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Flowline.MODID)
public class Flowline {
    public static final String MODID = "flowline";

    public Flowline() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ModNetwork::register));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlowlineConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FlowlineConfig.Client.SPEC);
    }
}
