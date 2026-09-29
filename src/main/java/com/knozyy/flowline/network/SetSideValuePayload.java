package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: set a number (priority, regulator, rate) of the side in the open pipe menu. */
public record SetSideValuePayload(int containerId, int field, int value) implements CustomPacketPayload {
    public static final Type<SetSideValuePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "set_side_value"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSideValuePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSideValuePayload::containerId,
            ByteBufCodecs.VAR_INT, SetSideValuePayload::field,
            ByteBufCodecs.INT, SetSideValuePayload::value,
            SetSideValuePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
