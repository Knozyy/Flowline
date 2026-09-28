package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SpeedTier;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Upgrade for an extracting side: faster transfers and access to the filter. Installed through the side's GUI
 * slot, or by right-clicking the side (which swaps out and returns the previous upgrade).
 */
public class UpgradeItem extends Item implements PipeInteractable {
    private final SpeedTier tier;

    /** Tier of the upgrade in {@code stack}, or {@link SpeedTier#BASE} if it is not an upgrade. */
    public static SpeedTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade ? upgrade.tier : SpeedTier.BASE;
    }

    public UpgradeItem(Properties properties, SpeedTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        if (conn != Conn.ENDPOINT) {
            player.displayClientMessage(Component.translatable("message.flowline.no_endpoint"), true);
            return;
        }
        if (pipe.side(side).mode != SideMode.EXTRACT) {
            player.displayClientMessage(Component.translatable("message.flowline.upgrade_needs_extract"), true);
            return;
        }
        if (pipe.getUpgrade(side).is(this)) {
            player.displayClientMessage(Component.translatable("message.flowline.upgrade_already"), true);
            return;
        }
        ItemStack old = pipe.setUpgrade(side, stack.copyWithCount(1));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        player.displayClientMessage(Component.translatable("message.flowline.upgrade_installed", tier.multiplier), true);
    }
}
