package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.JointRules;
import com.knozyy.flowline.curve.MeshQuad;
import com.knozyy.flowline.curve.TubeMesh;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The meshes of the curved pipes the client knows, built once per edge (and per joint) and kept until what they were
 * built from changes: the edge's synced copy, or the pieces its ends need. Holds no Minecraft client types, so a
 * GameTest can drive it.
 */
public final class MeshCache {
    private record EdgeEntry(CurveEdgesPayload.EdgeSync sync, TubeMesh.EndStyle start, TubeMesh.EndStyle end,
                             Vec3d startTangent, Vec3d endTangent, List<MeshQuad> quads, Aabb bounds) {}

    /** Two edges meeting at a node bend by more than this (cosine, about 18 degrees): drawn as a joint instead. */
    private static final double SMOOTH = 0.95;

    private record JointEntry(CurveEdgesPayload.NodeSync sync, List<Long> edges, List<MeshQuad> quads) {}

    private final ClientCurves curves;
    private final Map<Long, EdgeEntry> edges = new HashMap<>();
    private final Map<Long, JointEntry> joints = new HashMap<>();
    private int builds;

    public MeshCache(ClientCurves curves) {
        this.curves = curves;
    }

    /** The edge's quads, empty when the client doesn't know it. */
    public List<MeshQuad> edge(long id) {
        EdgeEntry entry = edgeEntry(id);
        return entry == null ? List.of() : entry.quads();
    }

    /** Where the edge's mesh lies, for skipping edges out of view; null when the client doesn't know it. */
    public Aabb bounds(long id) {
        EdgeEntry entry = edgeEntry(id);
        return entry == null ? null : entry.bounds();
    }

    private EdgeEntry edgeEntry(long id) {
        CurveEdgesPayload.EdgeSync sync = curves.edge(id);
        if (sync == null) {
            edges.remove(id);
            return null;
        }
        TubeMesh.EndStyle start = style(sync.from()), end = style(sync.to());
        Vec3d startTangent = shared(id, sync.from(), true), endTangent = shared(id, sync.to(), false);
        EdgeEntry entry = edges.get(id);
        if (entry != null && entry.sync() == sync && entry.start() == start && entry.end() == end
                && java.util.Objects.equals(entry.startTangent(), startTangent)
                && java.util.Objects.equals(entry.endTangent(), endTangent)) {
            return entry;
        }
        builds++;
        List<Vec3d> points = sync.points();
        entry = new EdgeEntry(sync, start, end, startTangent, endTangent,
                TubeMesh.edge(points, start, end, startTangent, endTangent),
                Aabb.of(points.toArray(new Vec3d[0])).inflate(CurveRules.DIAMETER));
        edges.put(id, entry);
        return entry;
    }

    /**
     * Whether the node is drawn as a glass joint: three or more edges meet there, it is forced, or two edges meet at a
     * sharp bend (their ends would not line up).
     */
    public boolean isJoint(long nodeId) {
        CurveEdgesPayload.NodeSync node = curves.node(nodeId);
        if (node == null) return false;
        List<CurveEdge> at = curves.graph().edgesAt(nodeId);
        return node.force() || at.size() >= 3 || at.size() == 2 && bend(nodeId, at) < SMOOTH;
    }

    /** The cosine of the bend between the two edges at a node: 1 when one carries straight on into the other. */
    private static double bend(long nodeId, List<CurveEdge> at) {
        return -CurveGraph.exitDirection(at.get(0), nodeId).dot(CurveGraph.exitDirection(at.get(1), nodeId));
    }

    /**
     * Where edge {@code id} meets one other edge through node {@code nodeId} smoothly, the direction both ends take
     * there (the middle of the two, pointing the way this edge runs) so their sections meet without a gap; else null.
     * The solver keeps the direction through a node only to a few degrees (Curvy's loose tolerance).
     */
    private Vec3d shared(long id, long nodeId, boolean leaving) {
        List<CurveEdge> at = curves.graph().edgesAt(nodeId);
        CurveEdgesPayload.NodeSync node = curves.node(nodeId);
        if (node == null || node.force() || node.anchorKind() != 0 || at.size() != 2 || bend(nodeId, at) < SMOOTH) {
            return null;
        }
        CurveEdge self = at.get(0).id() == id ? at.get(0) : at.get(1), other = self == at.get(0) ? at.get(1) : at.get(0);
        Vec3d mine = CurveGraph.exitDirection(self, nodeId), theirs = CurveGraph.exitDirection(other, nodeId);
        // the way this edge runs at the node: out of it when leaving, into it when arriving
        Vec3d along = mine.sub(theirs).normalize();
        return leaving ? along : along.scale(-1);
    }

    /** The joint's quads, empty unless {@link #isJoint}. */
    public List<MeshQuad> joint(long nodeId) {
        if (!isJoint(nodeId)) {
            joints.remove(nodeId);
            return List.of();
        }
        CurveEdgesPayload.NodeSync node = curves.node(nodeId);
        CurveGraph graph = curves.graph();
        List<Long> ids = new ArrayList<>();
        for (CurveEdge e : graph.edgesAt(nodeId)) ids.add(e.id());
        JointEntry entry = joints.get(nodeId);
        if (entry != null && entry.sync() == node && entry.edges().equals(ids)
                && ids.stream().allMatch(id -> edges.containsKey(id) && edges.get(id).sync() == curves.edge(id))) {
            return entry.quads();
        }
        builds++;
        List<Vec3d> exits = new ArrayList<>();
        double[] diameters = new double[ids.size()];
        int i = 0;
        for (CurveEdge e : graph.edgesAt(nodeId)) {
            exits.add(CurveGraph.exitDirection(e, nodeId));
            diameters[i++] = e.diameter();
        }
        // a two-edge bend gets the size of a forced joint: the plain radius would leave the duct's corners outside
        List<MeshQuad> quads = TubeMesh.joint(node.pos(), JointRules.jointRadius(diameters, node.force() || ids.size() == 2),
                exits);
        joints.put(nodeId, new JointEntry(node, ids, quads));
        return quads;
    }

    /** Drops the edge's mesh, so the next request builds it again. */
    public void invalidate(long id) {
        edges.remove(id);
    }

    /** Forgets meshes of edges and joints the client no longer has. */
    public void prune() {
        edges.keySet().removeIf(id -> curves.edge(id) == null);
        joints.keySet().removeIf(id -> curves.node(id) == null);
    }

    /** How many meshes were built; tests read it. */
    public int builds() {
        return builds;
    }

    /**
     * The piece at an edge's end: block face anchors get a collar (an extract ring when the end extracts), pipe faces
     * nothing, a free end with no other edge a glass cap, and a joint nothing (its sphere covers it).
     */
    private TubeMesh.EndStyle style(long nodeId) {
        CurveEdgesPayload.NodeSync node = curves.node(nodeId);
        if (node == null) return TubeMesh.EndStyle.NONE;
        int count = curves.graph().edgesAt(nodeId).size();
        if (node.force() || count >= 3) return TubeMesh.EndStyle.NONE;
        if (node.anchorKind() == 2) return node.extract() ? TubeMesh.EndStyle.RING : TubeMesh.EndStyle.COLLAR;
        if (node.anchorKind() == 1) return TubeMesh.EndStyle.NONE;
        return count == 1 ? TubeMesh.EndStyle.CAP : TubeMesh.EndStyle.NONE;
    }
}
