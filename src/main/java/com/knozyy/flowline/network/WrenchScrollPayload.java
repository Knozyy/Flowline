package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the player scrolled while sneaking with the wrench and looking at a pipe side. Extracting sides
 * cycle their distribution ({@code redstone}: their redstone mode), inserting sides change their priority.
 */
public record WrenchScrollPayload(BlockPos pos, Direction side, boolean forward, boolean redstone)
        implements CustomPacketPayload {
    public static final Type<WrenchScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "wrench_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchScrollPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WrenchScrollPayload::pos,
            Direction.STREAM_CODEC, WrenchScrollPayload::side,
            ByteBufCodecs.BOOL, WrenchScrollPayload::forward,
            ByteBufCodecs.BOOL, WrenchScrollPayload::redstone,
            WrenchScrollPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
