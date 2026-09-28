package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.item.WrenchItem;
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
    public static final DeferredItem<BlockItem> UNIVERSAL_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.UNIVERSAL_PIPE);

    public static final DeferredItem<WrenchItem> WRENCH =
            ITEMS.registerItem("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = upgrade("speed_upgrade", UpgradeType.SPEED);
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE = upgrade("stack_upgrade", UpgradeType.STACK);
    public static final DeferredItem<UpgradeItem> FILTER_UPGRADE = upgrade("filter_upgrade", UpgradeType.FILTER);
    public static final DeferredItem<UpgradeItem> KNOZY_UPGRADE = upgrade("knozy_upgrade", UpgradeType.KNOZY);

    public static final List<DeferredItem<UpgradeItem>> UPGRADES =
            List.of(SPEED_UPGRADE, STACK_UPGRADE, FILTER_UPGRADE, KNOZY_UPGRADE);

    private static DeferredItem<UpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.registerItem(name, props -> new UpgradeItem(props, type), new Item.Properties());
    }

    public static UpgradeItem upgrade(UpgradeType type) {
        return switch (type) {
            case SPEED -> SPEED_UPGRADE.get();
            case STACK -> STACK_UPGRADE.get();
            case FILTER -> FILTER_UPGRADE.get();
            case KNOZY -> KNOZY_UPGRADE.get();
        };
    }

    private ModItems() {}
}
