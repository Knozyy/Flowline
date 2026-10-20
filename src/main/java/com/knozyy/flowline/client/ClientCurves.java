package com.knozyy.flowline.client;

import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.network.curve.CurveResultPayload;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of the curved pipes it can see, fed by {@link CurveEdgesPayload}s. An edge or node stays while
 * at least one chunk it was sent with is watched. Pure data: drawing and picking read {@link #graph()}.
 */
public final class ClientCurves {
    public static final ClientCurves INSTANCE = new ClientCurves();

    private final CurveGraph graph = new CurveGraph();
    private final Map<Long, CurveEdgesPayload.NodeSync> nodes = new HashMap<>();
    private final Map<Long, CurveEdgesPayload.EdgeSync> edges = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> edgeSeen = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> nodeSeen = new HashMap<>();
    /** The last edit result the server sent, for the HUD. */
    @Nullable
    public CurveResultPayload lastResult;
    /** Counts changes, so the renderer knows when to drop meshes of what went away. */
    private int version;

    public int version() {
        return version;
    }

    public CurveGraph graph() {
        return graph;
    }

    @Nullable
    public CurveEdgesPayload.NodeSync node(long id) {
        return nodes.get(id);
    }

    @Nullable
    public CurveEdgesPayload.EdgeSync edge(long id) {
        return edges.get(id);
    }

    public void apply(CurveEdgesPayload payload) {
        version++;
        for (long id : payload.removedEdges()) removeEdge(id);
        for (long id : payload.removedNodes()) removeNode(id);
        for (CurveEdgesPayload.NodeSync node : payload.nodes()) {
            nodes.put(node.id(), node);
            nodeSeen.computeIfAbsent(node.id(), k -> new HashSet<>()).add(payload.chunk());
            graph.putNode(new CurveNode(node.id(), node.pos(), node.force()));
        }
        for (CurveEdgesPayload.EdgeSync edge : payload.edges()) {
            if (graph.node(edge.from()).isEmpty() || graph.node(edge.to()).isEmpty()) continue;
            edges.put(edge.id(), edge);
            edgeSeen.computeIfAbsent(edge.id(), k -> new HashSet<>()).add(payload.chunk());
            graph.putEdge(new CurveEdge(edge.id(), edge.from(), edge.to(), CurveRules.DIAMETER,
                    new SolvedSpan(edge.points(), edge.step())));
        }
    }

    /** The chunk is no longer watched: what only it showed goes. */
    public void forget(ChunkPos chunk) {
        version++;
        for (long id : new ArrayList<>(edgeSeen.keySet())) {
            Set<ChunkPos> seen = edgeSeen.get(id);
            if (seen.remove(chunk) && seen.isEmpty()) removeEdge(id);
        }
        for (long id : new ArrayList<>(nodeSeen.keySet())) {
            Set<ChunkPos> seen = nodeSeen.get(id);
            if (seen.remove(chunk) && seen.isEmpty() && graph.edgesAt(id).isEmpty()) removeNode(id);
        }
    }

    public void reset() {
        version++;
        for (long id : new ArrayList<>(edges.keySet())) removeEdge(id);
        for (long id : new ArrayList<>(nodes.keySet())) removeNode(id);
        lastResult = null;
    }

    private void removeEdge(long id) {
        edges.remove(id);
        edgeSeen.remove(id);
        graph.removeEdge(id);
    }

    private void removeNode(long id) {
        for (CurveEdge edge : List.copyOf(graph.edgesAt(id))) removeEdge(edge.id());
        nodes.remove(id);
        nodeSeen.remove(id);
        if (graph.node(id).isPresent()) graph.removeNode(id);
    }
}
