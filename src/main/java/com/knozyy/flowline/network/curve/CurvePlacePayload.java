package com.knozyy.flowline.network.curve;

import com.knozyy.flowline.curved.PlaceRequest;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Client → server: lay a curved pipe. Answered with a {@link CurveResultPayload} carrying {@code requestId}. */
public record CurvePlacePayload(int requestId, PlaceRequest request) {
    /** At most this many inner points per request. */
    public static final int MAX_INTERNALS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeEnum(request.type());
        CurveCodec.end(buf, request.start());
        CurveCodec.end(buf, request.end());
        buf.writeVarInt(request.internals().size());
        for (Vec3 p : request.internals()) CurveCodec.vec(buf, p);
    }

    public static CurvePlacePayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        PipeType type = buf.readEnum(PipeType.class);
        PlaceRequest.End start = CurveCodec.end(buf), end = CurveCodec.end(buf);
        int count = Math.min(buf.readVarInt(), MAX_INTERNALS);
        List<Vec3> internals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) internals.add(CurveCodec.vec(buf));
        return new CurvePlacePayload(id, new PlaceRequest(type, start, end, internals));
    }
}
