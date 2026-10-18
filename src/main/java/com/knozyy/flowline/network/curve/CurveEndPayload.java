package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: use the curved end on node {@code node} like a pipe side (open its screen, or cycle its mode). */
public record CurveEndPayload(int requestId, long node, Action action) {
    public enum Action { OPEN, CYCLE }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(node);
        buf.writeEnum(action);
    }

    public static CurveEndPayload decode(FriendlyByteBuf buf) {
        return new CurveEndPayload(buf.readVarInt(), buf.readVarLong(), buf.readEnum(Action.class));
    }
}
