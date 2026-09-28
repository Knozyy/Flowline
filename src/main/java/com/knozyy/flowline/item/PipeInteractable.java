package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Implemented by items that configure one side of a pipe when used on it. Server side only. */
public interface PipeInteractable {
    void useOnPipe(PipeBlockEntity pipe, Direction side, Player player, InteractionHand hand, ItemStack stack);
}
