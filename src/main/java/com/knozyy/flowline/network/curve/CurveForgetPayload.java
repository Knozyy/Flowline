package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;

/** Server → client: the player stopped watching {@code chunk}; what was sent for it can go. */
public record CurveForgetPayload(ChunkPos chunk) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeChunkPos(chunk);
    }

    public static CurveForgetPayload decode(FriendlyByteBuf buf) {
        return new CurveForgetPayload(buf.readChunkPos());
    }
}
