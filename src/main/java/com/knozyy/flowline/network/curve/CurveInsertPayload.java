package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: put a node {@code along} (0–1) segment {@code segment} of edge {@code edge}. */
public record CurveInsertPayload(int requestId, long edge, int segment, double along) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(edge);
        buf.writeVarInt(segment);
        buf.writeDouble(along);
    }

    public static CurveInsertPayload decode(FriendlyByteBuf buf) {
        return new CurveInsertPayload(buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readDouble());
    }
}
