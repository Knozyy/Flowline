package com.knozyy.flowline.menu;

import com.knozyy.flowline.pipe.Port;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link PipeMenu} configures, on the server: a block pipe's side or a curved pipe's end. Both have the same
 * {@link com.knozyy.flowline.pipe.SideConfig}, upgrade slots and screen.
 */
public interface SideHost {
    Port port();

    Container upgrades();

    /** The container slot of this end's upgrade slot {@code index}. */
    int upgradeSlot(int index);

    boolean accepts(ItemStack stack);

    boolean stillValid(Player player);

    /** The settings changed: save them (and show them where needed). */
    void changed();

    @Nullable
    Level level();

    /** Block and face the screen names: the pipe and its side, or the block a curved end sits on and that face. */
    BlockPos pos();

    Direction side();

    /** Data the client menu is built from: kind, then where it is. */
    void writeOpenData(FriendlyByteBuf buf);
}
