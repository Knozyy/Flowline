package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The client's guess at what the server will say about a chain: solved the same way (Curvy's checks 1 and 5-8), with
 * stand-in ids for the new nodes and edges. Reach and the cost are the client's to show; the server has the last word.
 */
public record ChainPreview(List<SolvedSpan> spans, Optional<PlaceError> error, int cost) {
    public ChainPreview {
        spans = List.copyOf(spans);
    }

    /**
     * One end of the chain: an existing node to continue ({@code node >= 0}), or a new one, maybe on a block face, or
     * on an existing edge that is split there ({@code splitEdge >= 0}; the halves are taken as the old edge).
     */
    public record End(Vec3d pos, long node, EndCondition condition, FaceAttachment face, long splitEdge) {
        public End(Vec3d pos, long node, EndCondition condition, FaceAttachment face) {
            this(pos, node, condition, face, -1);
        }
    }

    /** Stand-in ids below every real id. */
    private static final long FIRST_ID = -2;

    public static ChainPreview of(CurveGraph graph, BlockShapes world, End start, List<Vec3d> internals, End end) {
        List<Vec3d> points = new ArrayList<>();
        points.add(start.pos());
        points.addAll(internals);
        points.add(end.pos());
        Optional<SolvedChain> chain = CurveSolver.solve(points, CurveRules.DIAMETER, start.condition(), end.condition());
        if (chain.isEmpty()) return new ChainPreview(List.of(), Optional.of(PlaceError.INVALID_SHAPE), 0);
        List<SolvedSpan> spans = chain.get().spans();

        long next = FIRST_ID;
        long[] ids = new long[points.size()];
        List<CurveNode> nodes = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            long existing = i == 0 ? start.node() : i == points.size() - 1 ? end.node() : -1;
            ids[i] = existing >= 0 ? existing : next--;
            if (existing < 0) nodes.add(new CurveNode(ids[i], points.get(i), false));
        }
        List<CurveEdge> edges = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            edges.add(new CurveEdge(next--, ids[i], ids[i + 1], CurveRules.DIAMETER, spans.get(i)));
        }
        Map<Long, FaceAttachment> faces = new HashMap<>();
        if (start.face() != null) faces.put(ids[0], start.face());
        if (end.face() != null) faces.put(ids[ids.length - 1], end.face());
        Set<Long> split = new java.util.HashSet<>();
        if (start.splitEdge() >= 0) split.add(start.splitEdge());
        if (end.splitEdge() >= 0) split.add(end.splitEdge());
        Optional<PlaceError> error = PlacementValidator.validate(graph,
                new PlacementCandidate(nodes, edges, split, faces), world);
        return new ChainPreview(spans, error, CostPlan.required(spans, List.of()));
    }
}
