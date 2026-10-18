package com.knozyy.flowline.network.curve;

import com.knozyy.flowline.curved.PlaceRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Client → server: move curved node {@code node} to {@code pos}, fixed to {@code face} if given. */
public record CurveTranslatePayload(int requestId, long node, Vec3 pos, Optional<PlaceRequest.Face> face) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarLong(node);
        CurveCodec.vec(buf, pos);
        CurveCodec.face(buf, face);
    }

    public static CurveTranslatePayload decode(FriendlyByteBuf buf) {
        return new CurveTranslatePayload(buf.readVarInt(), buf.readVarLong(), CurveCodec.vec(buf), CurveCodec.face(buf));
    }
}
