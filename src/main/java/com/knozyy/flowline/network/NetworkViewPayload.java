package com.knozyy.flowline.network;

import com.knozyy.flowline.pipe.NetworkView;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record NetworkViewPayload(ResourceLocation dimension, BlockPos origin, NetworkView.Snapshot view) {
    public void encode(FriendlyByteBuf buf) {
        if (view.entries().size() > NetworkView.MAX_ENTRIES) throw new IllegalArgumentException("Network view too large");
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(origin);
        buf.writeBoolean(view.truncated());
        buf.writeVarInt(view.entries().size());
        for (NetworkView.Entry entry : view.entries()) {
            buf.writeBlockPos(entry.pos());
            buf.writeByte(entry.roles());
            buf.writeInt(entry.priority());
        }
    }

    public static NetworkViewPayload decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        BlockPos origin = buf.readBlockPos();
        boolean truncated = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > NetworkView.MAX_ENTRIES) throw new DecoderException("Invalid network view size");
        List<NetworkView.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            int roles = buf.readUnsignedByte();
            if (roles == 0 || (roles & ~15) != 0) throw new DecoderException("Invalid network role");
            entries.add(new NetworkView.Entry(pos, roles, buf.readInt()));
        }
        return new NetworkViewPayload(dimension, origin, new NetworkView.Snapshot(entries, truncated));
    }
}
