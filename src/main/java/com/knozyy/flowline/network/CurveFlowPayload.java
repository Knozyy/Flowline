package com.knozyy.flowline.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import io.netty.handler.codec.DecoderException;
import java.util.*;

/** A past transfer along directed curve edges. Rendering never performs an inventory operation. */
public record CurveFlowPayload(ResourceLocation dimension, int from, List<Integer> edges, ItemStack item, FluidStack fluid) {
    public CurveFlowPayload { edges=List.copyOf(edges);item=item.copy();fluid=fluid.copy(); }
    public void encode(FriendlyByteBuf b){ b.writeResourceLocation(dimension);b.writeVarInt(from);b.writeVarInt(edges.size());
        for(int id:edges)b.writeVarInt(id);b.writeItem(item);b.writeFluidStack(fluid); }
    public static CurveFlowPayload decode(FriendlyByteBuf b){
        ResourceLocation dim=b.readResourceLocation();int from=b.readVarInt(),n=b.readVarInt();
        if(from<=0||n<1||n>256)throw new DecoderException("Invalid curve flow");
        List<Integer> edges=new ArrayList<>();for(int i=0;i<n;i++){int id=b.readVarInt();if(id<=0)throw new DecoderException("Invalid flow edge");edges.add(id);}
        return new CurveFlowPayload(dim,from,edges,b.readItem(),b.readFluidStack());
    }
}
