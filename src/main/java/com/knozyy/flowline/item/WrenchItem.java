package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Click: cycle the side mode (insert / extract / disabled). Sneak-click: cycle the distribution mode. */
public class WrenchItem extends Item implements PipeInteractable {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Player player, InteractionHand hand, ItemStack stack) {
        SideConfig cfg = pipe.side(side);
        if (player.isShiftKeyDown()) {
            cfg.distribution = cfg.distribution.next();
        } else {
            cfg.mode = cfg.mode.next();
        }
        pipe.setChanged();
        player.displayClientMessage(Component.translatable("message.flowline.side_info",
                Component.translatable("direction.flowline." + side.getName()),
                Component.translatable("mode.flowline." + cfg.mode.name().toLowerCase()),
                Component.translatable("distribution.flowline." + cfg.distribution.name().toLowerCase()),
                cfg.speed.multiplier), true);
    }
}
