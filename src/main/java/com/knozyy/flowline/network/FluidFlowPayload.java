package com.knozyy.flowline.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public record FluidFlowPayload(ResourceLocation dimension, Fluid fluid, List<BlockPos> path) {
    public static final int MAX_PATH = 260;

    public FluidFlowPayload { path = List.copyOf(path); }

    public void encode(FriendlyByteBuf buf) {
        if (path.size() < 3 || path.size() > MAX_PATH) throw new IllegalArgumentException("Invalid fluid path size");
        buf.writeResourceLocation(dimension);
        buf.writeResourceLocation(BuiltInRegistries.FLUID.getKey(fluid));
        buf.writeVarInt(path.size());
        for (BlockPos pos : path) buf.writeBlockPos(pos);
    }

    public static FluidFlowPayload decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        Fluid fluid = BuiltInRegistries.FLUID.get(buf.readResourceLocation());
        if (fluid == Fluids.EMPTY) throw new DecoderException("Unknown flowing fluid");
        int count = buf.readVarInt();
        if (count < 3 || count > MAX_PATH) throw new DecoderException("Invalid fluid path size");
        List<BlockPos> path = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            if (i > 0 && pos.distManhattan(path.get(i - 1)) != 1) throw new DecoderException("Disconnected fluid path");
            path.add(pos);
        }
        return new FluidFlowPayload(dimension, fluid, path);
    }
}
