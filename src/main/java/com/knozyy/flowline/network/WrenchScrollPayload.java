package com.knozyy.flowline.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client → server: the player scrolled while sneaking with the wrench and looking at a pipe side. Extracting sides
 * cycle their distribution ({@code redstone}: their redstone mode), inserting sides change their priority.
 */
public record WrenchScrollPayload(BlockPos pos, Direction side, boolean forward, boolean redstone) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
        buf.writeBoolean(forward);
        buf.writeBoolean(redstone);
    }

    public static WrenchScrollPayload decode(FriendlyByteBuf buf) {
        return new WrenchScrollPayload(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean(),
                buf.readBoolean());
    }
}
