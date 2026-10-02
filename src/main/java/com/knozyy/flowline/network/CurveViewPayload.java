package com.knozyy.flowline.network;

import com.knozyy.flowline.curve.CurveGeometry;
import com.knozyy.flowline.pipe.PipeType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded local graph view, not inventories or filter contents. Geometry tangents are authoritative. */
public record CurveViewPayload(ResourceLocation dimension, int cursor, List<Node> nodes, List<Edge> edges, boolean limited) {
    public static final int MAX_EDGES=512, MAX_NODES=1024;
    public record Node(int id, PipeType type, Vec3 point, BlockPos block, Direction face, int flags) {}
    public record Edge(int id, int a, int b, Vec3 ta, Vec3 tb) {}
    public CurveViewPayload { nodes=List.copyOf(nodes); edges=List.copyOf(edges); }
    public static void point(FriendlyByteBuf b, Vec3 p) { b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z); }
    public static Vec3 point(FriendlyByteBuf b) {
        Vec3 p=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        if(!CurveGeometry.finite(p)) throw new DecoderException("Invalid curve coordinate"); return p;
    }
    private static int count(FriendlyByteBuf b,int max) { int n=b.readVarInt(); if(n<0||n>max)throw new DecoderException("Curve view exceeds bound");return n; }
    public void encode(FriendlyByteBuf b) {
        if(nodes.size()>MAX_NODES||edges.size()>MAX_EDGES)throw new IllegalArgumentException("Curve view exceeds bound");
        b.writeResourceLocation(dimension);b.writeVarInt(cursor);b.writeBoolean(limited);b.writeVarInt(nodes.size());
        for(Node n:nodes){b.writeVarInt(n.id);b.writeEnum(n.type);point(b,n.point);b.writeBoolean(n.block!=null);
            if(n.block!=null){b.writeBlockPos(n.block);b.writeEnum(n.face);}b.writeByte(n.flags);}
        b.writeVarInt(edges.size());for(Edge e:edges){b.writeVarInt(e.id);b.writeVarInt(e.a);b.writeVarInt(e.b);point(b,e.ta);point(b,e.tb);}
    }
    public static CurveViewPayload decode(FriendlyByteBuf b) {
        ResourceLocation dim=b.readResourceLocation();int cursor=b.readVarInt();boolean limited=b.readBoolean();
        List<Node> nodes=new ArrayList<>();Set<Integer> ids=new HashSet<>();int n=count(b,MAX_NODES);
        for(int i=0;i<n;i++){
            int id=b.readVarInt();if(id<=0||!ids.add(id))throw new DecoderException("Invalid curve node");
            PipeType type=b.readEnum(PipeType.class);Vec3 p=point(b);BlockPos block=b.readBoolean()?b.readBlockPos():null;
            Direction face=block==null?Direction.UP:b.readEnum(Direction.class);int flags=b.readUnsignedByte();nodes.add(new Node(id,type,p,block,face,flags));
        }
        List<Edge> edges=new ArrayList<>();Set<Integer> eids=new HashSet<>();int m=count(b,MAX_EDGES);
        for(int i=0;i<m;i++){
            int id=b.readVarInt(),a=b.readVarInt(),c=b.readVarInt();Vec3 ta=point(b),tb=point(b);
            if(id<=0||a==c||!eids.add(id)||!ids.contains(a)||!ids.contains(c)||ta.length()>32||tb.length()>32)
                throw new DecoderException("Invalid curve edge");
            edges.add(new Edge(id,a,c,ta,tb));
        }
        return new CurveViewPayload(dim,cursor,nodes,edges,limited);
    }
}
