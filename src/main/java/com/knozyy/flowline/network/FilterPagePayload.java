package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Server → client: the filter rules on the visible page of the open pipe menu, so the editor can show them. */
public record FilterPagePayload(int containerId, int page, List<Optional<FilterEntry>> entries)
        implements CustomPacketPayload {
    public static final Type<FilterPagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "filter_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterPagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FilterPagePayload::containerId,
                    ByteBufCodecs.VAR_INT, FilterPagePayload::page,
                    ByteBufCodecs.optional(FilterEntry.STREAM_CODEC).apply(ByteBufCodecs.list(64)),
                    FilterPagePayload::entries,
                    FilterPagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
