package com.knozyy.flowline.network.curve;

import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.PlaceRequest;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalLong;

/** Shared pieces of the curved pipe packets. Doubles travel as they are, so shapes arrive bit for bit. */
final class CurveCodec {
    private CurveCodec() {}

    static void vec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    static Vec3 vec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static void vec3d(FriendlyByteBuf buf, Vec3d v) {
        buf.writeDouble(v.x());
        buf.writeDouble(v.y());
        buf.writeDouble(v.z());
    }

    static Vec3d vec3d(FriendlyByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static void face(FriendlyByteBuf buf, Optional<PlaceRequest.Face> face) {
        buf.writeBoolean(face.isPresent());
        face.ifPresent(f -> {
            buf.writeBlockPos(f.pos());
            buf.writeEnum(f.dir());
        });
    }

    static Optional<PlaceRequest.Face> face(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return Optional.empty();
        return Optional.of(new PlaceRequest.Face(buf.readBlockPos(), buf.readEnum(Direction.class)));
    }

    static void end(FriendlyByteBuf buf, PlaceRequest.End end) {
        vec(buf, end.pos());
        buf.writeBoolean(end.node().isPresent());
        if (end.node().isPresent()) buf.writeVarLong(end.node().getAsLong());
        face(buf, end.face());
        buf.writeBoolean(end.split().isPresent());
        end.split().ifPresent(s -> {
            buf.writeVarLong(s.edge());
            buf.writeVarInt(s.segment());
            buf.writeDouble(s.along());
        });
    }

    static PlaceRequest.End end(FriendlyByteBuf buf) {
        Vec3 pos = vec(buf);
        OptionalLong node = buf.readBoolean() ? OptionalLong.of(buf.readVarLong()) : OptionalLong.empty();
        Optional<PlaceRequest.Face> face = face(buf);
        Optional<PlaceRequest.Split> split = buf.readBoolean()
                ? Optional.of(new PlaceRequest.Split(buf.readVarLong(), buf.readVarInt(), buf.readDouble())) : Optional.empty();
        return new PlaceRequest.End(pos, node, face, split);
    }
}
