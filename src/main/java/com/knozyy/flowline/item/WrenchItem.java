package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Sneak-click an endpoint: toggle insert / extract. Click any side: cut or restore the connection. */
public class WrenchItem extends Item implements PipeInteractable {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        if (player.isShiftKeyDown()) {
            if (conn != Conn.ENDPOINT) {
                player.displayClientMessage(Component.translatable("message.flowline.no_endpoint"), true);
            } else {
                PipeBlock.toggleMode(pipe, side, player);
            }
            return;
        }
        Boolean connected = PipeBlock.toggleConnection(pipe.getLevel(), pipe.getBlockPos(), side);
        player.displayClientMessage(Component.translatable(connected == null ? "message.flowline.nothing_to_connect"
                : connected ? "message.flowline.connected" : "message.flowline.disconnected"), true);
    }
}
