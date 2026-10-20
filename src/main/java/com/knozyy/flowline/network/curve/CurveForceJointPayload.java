package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: turn curved node {@code node}'s forced joint on or off. */
public record CurveForceJointPayload(int requestId, long node, boolean desired) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(node);
        buf.writeBoolean(desired);
    }

    public static CurveForceJointPayload decode(FriendlyByteBuf buf) {
        return new CurveForceJointPayload(buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
    }
}
