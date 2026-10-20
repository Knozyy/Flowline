package com.knozyy.flowline.network.curve;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

/** Client to server: dye curved edge {@code edge} with the dye in {@code hand}. */
public record CurvePaintPayload(int requestId, long edge, InteractionHand hand) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(edge);
        buf.writeEnum(hand);
    }

    public static CurvePaintPayload decode(FriendlyByteBuf buf) {
        return new CurvePaintPayload(buf.readVarInt(), buf.readVarLong(), buf.readEnum(InteractionHand.class));
    }
}
