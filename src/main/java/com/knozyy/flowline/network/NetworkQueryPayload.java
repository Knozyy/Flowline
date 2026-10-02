package com.knozyy.flowline.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record NetworkQueryPayload(BlockPos pos) {
    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public static NetworkQueryPayload decode(FriendlyByteBuf buf) {
        return new NetworkQueryPayload(buf.readBlockPos());
    }
}
