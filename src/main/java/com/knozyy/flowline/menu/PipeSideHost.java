package com.knozyy.flowline.menu;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeSidePort;
import com.knozyy.flowline.pipe.Port;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Side {@code side} of a block pipe, configured by a {@link PipeMenu}. */
public record PipeSideHost(PipeBlockEntity pipe, Direction side) implements SideHost {
    static final byte KIND = 0;

    @Override
    public Port port() {
        return new PipeSidePort(pipe, side);
    }

    @Override
    public Container upgrades() {
        return pipe.upgrades();
    }

    @Override
    public int upgradeSlot(int index) {
        return PipeBlockEntity.upgradeSlot(side, index);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return pipe.accepts(side, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return pipe.menuValid(player);
    }

    @Override
    public void changed() {
        pipe.setChanged();
    }

    @Override
    public Level level() {
        return pipe.getLevel();
    }

    @Override
    public BlockPos pos() {
        return pipe.getBlockPos();
    }

    @Override
    public void writeOpenData(FriendlyByteBuf buf) {
        buf.writeByte(KIND);
        buf.writeBlockPos(pipe.getBlockPos());
        buf.writeEnum(side);
    }
}
