package com.knozyy.flowline.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → nearby clients: an item stack was moved along {@code path} (source block, pipes, target block). Purely
 * visual; the transfer itself already happened.
 */
public record TravelPayload(ItemStack stack, List<BlockPos> path) {
    /** Blocks around the extracting pipe within which players are told. */
    public static final double RANGE = 48;

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeVarInt(path.size());
        for (BlockPos pos : path) buf.writeBlockPos(pos);
    }

    public static TravelPayload decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int size = Math.min(260, buf.readVarInt());
        List<BlockPos> path = new ArrayList<>(size);
        for (int i = 0; i < size; i++) path.add(buf.readBlockPos());
        return new TravelPayload(stack, path);
    }
}
