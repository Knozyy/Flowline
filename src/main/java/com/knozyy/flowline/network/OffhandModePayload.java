package com.knozyy.flowline.network;

import com.knozyy.flowline.pipe.OffhandMode;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client → server: the off-hand pipe mode the player switched to (see {@link OffhandMode}) and, for a universal pipe
 * in Curvy mode, which channel it lays.
 */
public record OffhandModePayload(OffhandMode mode, int channel) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mode.ordinal());
        buf.writeVarInt(channel);
    }

    public static OffhandModePayload decode(FriendlyByteBuf buf) {
        return new OffhandModePayload(OffhandMode.byOrdinal(buf.readVarInt()), Math.max(0, Math.min(2, buf.readVarInt())));
    }
}
