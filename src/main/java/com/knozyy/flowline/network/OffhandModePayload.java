package com.knozyy.flowline.network;

import com.knozyy.flowline.pipe.OffhandMode;
import net.minecraft.network.FriendlyByteBuf;

/** Client → server: the off-hand pipe mode the player switched to (see {@link OffhandMode}). */
public record OffhandModePayload(OffhandMode mode) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mode.ordinal());
    }

    public static OffhandModePayload decode(FriendlyByteBuf buf) {
        return new OffhandModePayload(OffhandMode.byOrdinal(buf.readVarInt()));
    }
}
