package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server → nearby clients: an item stack was moved along {@code path} (source block, pipes, target block). Purely
 * visual; the transfer itself already happened.
 */
public record TravelPayload(ItemStack stack, List<BlockPos> path) implements CustomPacketPayload {
    /** Blocks around the extracting pipe within which players are told. */
    public static final double RANGE = 48;

    public static final Type<TravelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "travel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TravelPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, TravelPayload::stack,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(260)), TravelPayload::path,
            TravelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
