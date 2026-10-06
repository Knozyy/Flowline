package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import com.knozyy.flowline.item.FilterCardItem;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Flowline.MODID);

    public static final RegistryObject<Item> ITEM_PIPE = blockItem(ModBlocks.ITEM_PIPE);
    public static final RegistryObject<Item> FLUID_PIPE = blockItem(ModBlocks.FLUID_PIPE);
    public static final RegistryObject<Item> ENERGY_PIPE = blockItem(ModBlocks.ENERGY_PIPE);
    public static final RegistryObject<Item> UNIVERSAL_PIPE = blockItem(ModBlocks.UNIVERSAL_PIPE);
    @Nullable
    public static final RegistryObject<Item> CHEMICAL_PIPE =
            ModBlocks.CHEMICAL_PIPE == null ? null : blockItem(ModBlocks.CHEMICAL_PIPE);

    public static final RegistryObject<WrenchItem> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<UpgradeItem> SPEED_UPGRADE = upgrade("speed_upgrade", UpgradeType.SPEED);
    public static final RegistryObject<UpgradeItem> STACK_UPGRADE = upgrade("stack_upgrade", UpgradeType.STACK);
    public static final RegistryObject<UpgradeItem> FILTER_UPGRADE = upgrade("filter_upgrade", UpgradeType.FILTER);
    public static final RegistryObject<UpgradeItem> KNOZY_UPGRADE = upgrade("knozy_upgrade", UpgradeType.KNOZY);

    public static final List<RegistryObject<UpgradeItem>> UPGRADES =
            List.of(SPEED_UPGRADE, STACK_UPGRADE, FILTER_UPGRADE, KNOZY_UPGRADE);

    public static final RegistryObject<FilterCardItem> FILTER_CARD = ITEMS.register("filter_card",
            () -> new FilterCardItem(new Item.Properties().stacksTo(1)));

    private static RegistryObject<Item> blockItem(RegistryObject<PipeBlock> block) {
        return ITEMS.register(block.getId().getPath(), () -> CurvyPipesCompat.pipeItem(block.get(), block.getId().getPath()));
    }

    private static RegistryObject<UpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.register(name, () -> new UpgradeItem(new Item.Properties(), type));
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
