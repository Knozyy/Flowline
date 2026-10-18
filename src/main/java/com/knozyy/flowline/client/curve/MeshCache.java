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
                             List<MeshQuad> quads, Aabb bounds) {}

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
        EdgeEntry entry = edges.get(id);
        if (entry != null && entry.sync() == sync && entry.start() == start && entry.end() == end) return entry;
        builds++;
        List<Vec3d> points = sync.points();
        entry = new EdgeEntry(sync, start, end, TubeMesh.edge(points, start, end),
                Aabb.of(points.toArray(new Vec3d[0])).inflate(CurveRules.DIAMETER));
        edges.put(id, entry);
        return entry;
    }

    /** Whether the node is drawn as a glass joint: three or more edges meet there, or it is forced. */
    public boolean isJoint(long nodeId) {
        CurveEdgesPayload.NodeSync node = curves.node(nodeId);
        return node != null && (node.force() || curves.graph().edgesAt(nodeId).size() >= 3);
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
        List<MeshQuad> quads = TubeMesh.joint(node.pos(), JointRules.jointRadius(diameters, node.force()), exits);
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
