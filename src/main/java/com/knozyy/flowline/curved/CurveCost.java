package com.knozyy.flowline.curved;

import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * Pays for curved pipes with the player's own pipe items: every stack of that pipe they carry, the off hand first.
 * Refunds go back into the inventory and what does not fit drops at their feet. Creative players neither pay nor get.
 */
public final class CurveCost {
    private CurveCost() {}

    public static Item item(PipeType type) {
        for (RegistryObject<PipeBlock> pipe : ModBlocks.pipes()) {
            if (pipe.get().type() == type) return pipe.get().asItem();
        }
        throw new IllegalArgumentException("no pipe of type " + type);
    }

    public static int available(ServerPlayer player, PipeType type) {
        if (player.getAbilities().instabuild) return Integer.MAX_VALUE;
        return player.getInventory().countItem(item(type));
    }

    public static void take(ServerPlayer player, PipeType type, int count) {
        if (player.getAbilities().instabuild || count <= 0) return;
        Item item = item(type);
        Inventory inventory = player.getInventory();
        ItemStack off = player.getOffhandItem();
        if (off.is(item)) {
            int taken = Math.min(count, off.getCount());
            off.shrink(taken);
            count -= taken;
        }
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(count, stack.getCount());
            stack.shrink(taken);
            count -= taken;
        }
        inventory.setChanged();
    }

    public static void give(ServerPlayer player, PipeType type, int count) {
        if (player.getAbilities().instabuild || count <= 0) return;
        Item item = item(type);
        while (count > 0) {
            int part = Math.min(count, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, part);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            count -= part;
        }
    }
}
