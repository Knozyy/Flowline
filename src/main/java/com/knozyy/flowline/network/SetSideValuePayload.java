package com.knozyy.flowline.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: set a number (priority, regulator, rate) of the side in the open pipe menu. */
public record SetSideValuePayload(int containerId, int field, int value) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(field);
        buf.writeInt(value);
    }

    public static SetSideValuePayload decode(FriendlyByteBuf buf) {
        return new SetSideValuePayload(buf.readVarInt(), buf.readVarInt(), buf.readInt());
    }
}
