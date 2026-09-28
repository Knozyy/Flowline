package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SpeedTier;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Installs a speed tier on one side of a pipe. A better upgrade replaces (and refunds) a worse one. */
public class UpgradeItem extends Item implements PipeInteractable {
    private final SpeedTier tier;

    public UpgradeItem(Properties properties, SpeedTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Player player, InteractionHand hand, ItemStack stack) {
        SideConfig cfg = pipe.side(side);
        if (cfg.speed.ordinal() >= tier.ordinal()) {
            player.displayClientMessage(Component.translatable("message.flowline.upgrade_not_better"), true);
            return;
        }
        if (cfg.speed != SpeedTier.BASE) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.SPEED_UPGRADES.get(cfg.speed.ordinal() - 1).get()));
        }
        cfg.speed = tier;
        pipe.setChanged();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.flowline.upgrade_installed", tier.multiplier), true);
    }
}
