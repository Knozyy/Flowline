package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: break curved edge {@code edge}. */
public record CurveBreakPayload(int requestId, long edge) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(edge);
    }

    public static CurveBreakPayload decode(FriendlyByteBuf buf) {
        return new CurveBreakPayload(buf.readVarInt(), buf.readVarLong());
    }
}
