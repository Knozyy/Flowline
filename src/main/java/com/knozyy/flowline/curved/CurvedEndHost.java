package com.knozyy.flowline.curved;

import com.knozyy.flowline.menu.SideHost;
import com.knozyy.flowline.pipe.Port;
import com.knozyy.flowline.pipe.SideUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A curved pipe's end on a block face, configured by the same screen as a block pipe's side. */
public record CurvedEndHost(ServerLevel serverLevel, CurvedEnd end) implements SideHost {
    /** The open-data kind byte of a curved end (a block pipe side is 0). */
    public static final byte KIND = 1;

    @Override
    public Port port() {
        return end;
    }

    @Override
    public Container upgrades() {
        return end.upgrades();
    }

    @Override
    public int upgradeSlot(int index) {
        return index;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return SideUpgrades.accepts(end.cfg(), stack);
    }

    /** The end still exists and the player is within 8 blocks of its node. */
    @Override
    public boolean stillValid(Player player) {
        CurveData data = CurveData.get(serverLevel);
        if (data.end(end.nodeId()).orElse(null) != end) return false;
        return data.graph().node(end.nodeId())
                .map(n -> player.distanceToSqr(n.pos().x(), n.pos().y(), n.pos().z()) <= 64).orElse(false);
    }

    @Override
    public void changed() {
        CurveData.get(serverLevel).endChanged(end.nodeId());
    }

    @Override
    public Level level() {
        return serverLevel;
    }

    @Override
    public BlockPos pos() {
        return end.block();
    }

    @Override
    public Direction side() {
        return end.access();
    }

    @Override
    public void writeOpenData(FriendlyByteBuf buf) {
        buf.writeByte(KIND);
        buf.writeVarLong(end.nodeId());
        buf.writeBlockPos(end.block());
        buf.writeEnum(end.access());
    }
}
