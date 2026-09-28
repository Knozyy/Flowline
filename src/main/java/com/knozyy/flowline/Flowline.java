package com.knozyy.flowline;

import com.knozyy.flowline.registry.ModBlockEntities;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModCreativeTabs;
import com.knozyy.flowline.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Flowline.MODID)
public class Flowline {
    public static final String MODID = "flowline";

    public Flowline(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }
}
