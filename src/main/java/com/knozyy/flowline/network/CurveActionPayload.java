package com.knozyy.flowline.network;

import com.knozyy.flowline.curve.CurveGeometry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record CurveActionPayload(Action action, int id, Vec3 point, BlockPos block, Direction face, boolean finish) {
    public enum Action { REQUEST, ADD, SELECT, CONNECT, CANCEL, REMOVE_EDGE, REMOVE_NODE, MOVE, INSERT, JOINT, MODE, CONFIG }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(action); b.writeVarInt(id); b.writeDouble(point.x); b.writeDouble(point.y); b.writeDouble(point.z);
        b.writeBoolean(block != null); if(block != null) { b.writeBlockPos(block); b.writeEnum(face); }
        b.writeBoolean(finish);
    }
    public static CurveActionPayload decode(FriendlyByteBuf b) {
        Action action=b.readEnum(Action.class); int id=b.readVarInt(); Vec3 p=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        if (id < 0 || !CurveGeometry.finite(p)) throw new DecoderException("Invalid curve action");
        BlockPos pos=b.readBoolean()?b.readBlockPos():null; Direction face=pos==null?Direction.UP:b.readEnum(Direction.class);
        return new CurveActionPayload(action,id,p,pos,face,b.readBoolean());
    }
    public static CurveActionPayload simple(Action action, int id) { return new CurveActionPayload(action,id,Vec3.ZERO,null,Direction.UP,false); }
}
