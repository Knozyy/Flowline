package com.knozyy.flowline.curve;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A proposed change to the curve graph: new or moved nodes, new or reshaped edges (an edge with an existing id
 * replaces it), existing edges that go away, and the nodes that sit on block faces.
 */
public record PlacementCandidate(List<CurveNode> nodes, List<CurveEdge> edges, Set<Long> replacedEdges,
                                 Map<Long, FaceAttachment> attachments) {
    public PlacementCandidate {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        replacedEdges = Set.copyOf(replacedEdges);
        attachments = Map.copyOf(attachments);
    }
}
