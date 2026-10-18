package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server → client: how request {@code requestId} ended. {@code code} is a {@link com.knozyy.flowline.curve.PlaceError}
 * code, 0 when it worked; {@code missing} pipes when it was too costly.
 */
public record CurveResultPayload(int requestId, int code, boolean denied, int missing) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarInt(code);
        buf.writeBoolean(denied);
        buf.writeVarInt(missing);
    }

    public static CurveResultPayload decode(FriendlyByteBuf buf) {
        return new CurveResultPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }
}
