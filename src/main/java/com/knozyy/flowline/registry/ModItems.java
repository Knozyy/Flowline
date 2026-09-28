package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.FilterItem;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.pipe.SpeedTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Flowline.MODID);

    public static final DeferredItem<BlockItem> ITEM_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_PIPE);
    public static final DeferredItem<BlockItem> FLUID_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_PIPE);
    public static final DeferredItem<BlockItem> ENERGY_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.ENERGY_PIPE);

    public static final DeferredItem<WrenchItem> WRENCH =
            ITEMS.registerItem("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<FilterItem> FILTER =
            ITEMS.registerItem("filter", FilterItem::new, new Item.Properties().stacksTo(1));

    /** Indexed by {@code tier.ordinal() - 1}: tier 1, 2, 3. */
    public static final List<DeferredItem<UpgradeItem>> SPEED_UPGRADES = List.of(
            upgrade("speed_upgrade_1", SpeedTier.TIER_1),
            upgrade("speed_upgrade_2", SpeedTier.TIER_2),
            upgrade("speed_upgrade_3", SpeedTier.TIER_3));

    private static DeferredItem<UpgradeItem> upgrade(String name, SpeedTier tier) {
        return ITEMS.registerItem(name, props -> new UpgradeItem(props, tier), new Item.Properties());
    }

    private ModItems() {}
}
