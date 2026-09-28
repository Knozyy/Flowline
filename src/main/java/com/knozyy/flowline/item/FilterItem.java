package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Hold the filter in one hand and a sample in the other (an item, or a bucket/tank for fluid pipes).
 * Click: toggle the sample in the filter. Click with an empty off hand: clear the filter.
 * Sneak-click: switch between whitelist and blacklist.
 */
public class FilterItem extends Item implements PipeInteractable {
    public FilterItem(Properties properties) {
        super(properties);
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
        if (pipe.type() == PipeType.ENERGY) {
            player.displayClientMessage(Component.translatable("message.flowline.no_filter_energy"), true);
            return;
        }
        SideConfig cfg = pipe.side(side);

        if (player.isShiftKeyDown()) {
            cfg.whitelist = !cfg.whitelist;
            pipe.setChanged();
            player.displayClientMessage(Component.translatable(cfg.whitelist
                    ? "message.flowline.filter_whitelist" : "message.flowline.filter_blacklist"), true);
            return;
        }

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(other);
        if (sample.isEmpty()) {
            cfg.filter.clear();
            pipe.setChanged();
            player.displayClientMessage(Component.translatable("message.flowline.filter_cleared"), true);
            return;
        }
        if (!SideConfig.isValidSample(pipe.type(), sample)) {
            player.displayClientMessage(Component.translatable("message.flowline.filter_invalid_sample"), true);
            return;
        }

        int index = cfg.indexOfSample(sample);
        if (index >= 0) {
            cfg.filter.remove(index);
        } else if (cfg.isFilterFull()) {
            player.displayClientMessage(Component.translatable("message.flowline.filter_full"), true);
            return;
        } else {
            cfg.filter.add(sample.copyWithCount(1));
        }
        pipe.setChanged();
        player.displayClientMessage(Component.translatable(
                index < 0 ? "message.flowline.filter_added" : "message.flowline.filter_removed",
                sample.getHoverName()), true);
    }
}
