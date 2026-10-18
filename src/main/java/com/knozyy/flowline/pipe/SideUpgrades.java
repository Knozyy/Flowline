package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** The upgrade rules shared by block pipe sides and curved pipe ends. */
public final class SideUpgrades {
    private SideUpgrades() {}

    /** Extracting ends take every upgrade; inserting ends only Filter upgrades (for more insert rules). */
    public static boolean accepts(SideConfig cfg, ItemStack stack) {
        UpgradeType upgrade = UpgradeItem.typeOf(stack);
        if (upgrade == null) return false;
        return cfg.mode == SideMode.EXTRACT || upgrade == UpgradeType.FILTER;
    }

    /** The end's Speed, Stack and Filter counts mirror the upgrades in its {@link SideConfig#UPGRADE_SLOTS} slots. */
    public static void sync(SideConfig cfg, Container upgrades, int firstSlot) {
        int speed = 0, stack = 0, filter = 0;
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            UpgradeType type = UpgradeItem.typeOf(upgrades.getItem(firstSlot + i));
            if (type != null) {
                speed += type.speed;
                stack += type.stack;
                filter += type.filter;
            }
        }
        cfg.filterCount = filter;
        if (speed != cfg.speedCount || stack != cfg.stackCount) {
            cfg.speedCount = speed;
            cfg.stackCount = stack;
            cfg.wake();
        }
    }
}
