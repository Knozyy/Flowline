package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: remove curved node {@code node}. */
public record CurveRemovePayload(int requestId, long node) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(node);
    }

    public static CurveRemovePayload decode(FriendlyByteBuf buf) {
        return new CurveRemovePayload(buf.readVarInt(), buf.readVarLong());
    }
}
