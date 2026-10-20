package com.knozyy.flowline.network.curve;

import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the curved pipes of {@code chunk} as they are now (edges reaching it and their nodes), and the ones
 * that went away.
 */
public record CurveEdgesPayload(ChunkPos chunk, List<EdgeSync> edges, List<NodeSync> nodes, List<Long> removedEdges,
                                List<Long> removedNodes) {
    /** A solved edge: its ends, what it carries, its dye colour and its points. */
    public record EdgeSync(long id, long from, long to, PipeType type, int color, double step, List<Vec3d> points) {}

    /** A node: where it is, whether it is a forced joint, how it is fixed (0 free, 1 pipe face, 2 block face). */
    public record NodeSync(long id, Vec3d pos, boolean force, byte anchorKind, boolean extract) {}

    public CurveEdgesPayload {
        edges = List.copyOf(edges);
        nodes = List.copyOf(nodes);
        removedEdges = List.copyOf(removedEdges);
        removedNodes = List.copyOf(removedNodes);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeChunkPos(chunk);
        buf.writeVarInt(edges.size());
        for (EdgeSync e : edges) {
            buf.writeVarLong(e.id());
            buf.writeVarLong(e.from());
            buf.writeVarLong(e.to());
            buf.writeEnum(e.type());
            buf.writeInt(e.color());
            buf.writeDouble(e.step());
            buf.writeVarInt(e.points().size());
            for (Vec3d p : e.points()) CurveCodec.vec3d(buf, p);
        }
        buf.writeVarInt(nodes.size());
        for (NodeSync n : nodes) {
            buf.writeVarLong(n.id());
            CurveCodec.vec3d(buf, n.pos());
            buf.writeBoolean(n.force());
            buf.writeByte(n.anchorKind());
            buf.writeBoolean(n.extract());
        }
        buf.writeVarInt(removedEdges.size());
        for (long id : removedEdges) buf.writeVarLong(id);
        buf.writeVarInt(removedNodes.size());
        for (long id : removedNodes) buf.writeVarLong(id);
    }

    public static CurveEdgesPayload decode(FriendlyByteBuf buf) {
        ChunkPos chunk = buf.readChunkPos();
        int edgeCount = buf.readVarInt();
        List<EdgeSync> edges = new ArrayList<>();
        for (int i = 0; i < edgeCount; i++) {
            long id = buf.readVarLong(), from = buf.readVarLong(), to = buf.readVarLong();
            PipeType type = buf.readEnum(PipeType.class);
            int color = buf.readInt();
            double step = buf.readDouble();
            int pointCount = buf.readVarInt();
            List<Vec3d> points = new ArrayList<>(pointCount);
            for (int k = 0; k < pointCount; k++) points.add(CurveCodec.vec3d(buf));
            edges.add(new EdgeSync(id, from, to, type, color, step, List.copyOf(points)));
        }
        int nodeCount = buf.readVarInt();
        List<NodeSync> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new NodeSync(buf.readVarLong(), CurveCodec.vec3d(buf), buf.readBoolean(), buf.readByte(), buf.readBoolean()));
        }
        int removedEdgeCount = buf.readVarInt();
        List<Long> removedEdges = new ArrayList<>();
        for (int i = 0; i < removedEdgeCount; i++) removedEdges.add(buf.readVarLong());
        int removedNodeCount = buf.readVarInt();
        List<Long> removedNodes = new ArrayList<>();
        for (int i = 0; i < removedNodeCount; i++) removedNodes.add(buf.readVarLong());
        return new CurveEdgesPayload(chunk, edges, nodes, removedEdges, removedNodes);
    }
}
