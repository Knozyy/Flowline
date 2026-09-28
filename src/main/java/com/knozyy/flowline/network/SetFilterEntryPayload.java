package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Client → server: set (or clear, when empty) the filter rule at {@code index} of the open pipe menu. */
public record SetFilterEntryPayload(int containerId, int index, Optional<FilterEntry> entry)
        implements CustomPacketPayload {
    public static final Type<SetFilterEntryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "set_filter_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetFilterEntryPayload::containerId,
                    ByteBufCodecs.VAR_INT, SetFilterEntryPayload::index,
                    ByteBufCodecs.optional(FilterEntry.STREAM_CODEC), SetFilterEntryPayload::entry,
                    SetFilterEntryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
