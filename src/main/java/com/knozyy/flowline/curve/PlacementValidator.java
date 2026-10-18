package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Curvy Pipes' geometric placement checks (codes 5–8) on a proposed change: block faces, blocks, other pipes, joints
 * and angles. The caller checks the object (2), dimension and reach (4), shape (1) before and the cost (3) after.
 */
public final class PlacementValidator {
    private PlacementValidator() {}

    /**
     * Whether {@code p} lies on face {@code face} of one of the outline boxes: on the face's plane and within the face,
     * both to {@link CurveRules#FACE_TOLERANCE}.
     */
    public static boolean onFace(Vec3d p, int face, List<Aabb> outline) {
        double tol = CurveRules.FACE_TOLERANCE;
        for (Aabb b : outline) {
            Aabb plane = switch (face) {
                case 0 -> new Aabb(b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.maxZ());
                case 1 -> new Aabb(b.minX(), b.maxY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
                case 2 -> new Aabb(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.minZ());
                case 3 -> new Aabb(b.minX(), b.minY(), b.maxZ(), b.maxX(), b.maxY(), b.maxZ());
                case 4 -> new Aabb(b.minX(), b.minY(), b.minZ(), b.minX(), b.maxY(), b.maxZ());
                case 5 -> new Aabb(b.maxX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
                default -> throw new IllegalArgumentException("face " + face);
            };
            if (plane.inflate(tol).contains(p)) return true;
        }
        return false;
    }

    /** The first rule the change breaks, or empty when it may go ahead. */
    public static Optional<PlaceError> validate(CurveGraph graph, PlacementCandidate c, BlockShapes world) {
        Map<Long, CurveNode> moved = new HashMap<>();
        for (CurveNode node : c.nodes()) moved.put(node.id(), node);
        Set<Long> gone = new HashSet<>(c.replacedEdges());
        for (CurveEdge edge : c.edges()) gone.add(edge.id());

        for (Map.Entry<Long, FaceAttachment> entry : c.attachments().entrySet()) {
            FaceAttachment at = entry.getValue();
            Vec3d pos = node(graph, moved, entry.getKey()).pos();
            if (!onFace(pos, at.face(), world.outline(at.x(), at.y(), at.z()))) return Optional.of(PlaceError.INTERSECT_BLOCK);
        }

        List<List<Obb>> shapes = new ArrayList<>();
        for (CurveEdge edge : c.edges()) shapes.add(EdgeShape.boxes(edge.shape(), 0.5 * edge.diameter()));
        for (List<Obb> shape : shapes) {
            for (Obb box : shape) if (hitsBlock(box, world)) return Optional.of(PlaceError.INTERSECT_BLOCK);
        }

        for (int i = 0; i < c.edges().size(); i++) {
            CurveEdge edge = c.edges().get(i);
            for (Obb box : shapes.get(i)) {
                for (SegmentBox other : graph.boxesNear(box.bounds())) {
                    if (gone.contains(other.edgeId()) || sharesNode(edge, graph.edge(other.edgeId()).orElseThrow())) continue;
                    if (box.overlaps(other.box())) return Optional.of(PlaceError.INTERSECT_PIPE);
                }
            }
            for (int j = i + 1; j < c.edges().size(); j++) {
                if (sharesNode(edge, c.edges().get(j))) continue;
                for (Obb a : shapes.get(i)) {
                    for (Obb b : shapes.get(j)) if (a.overlaps(b)) return Optional.of(PlaceError.INTERSECT_PIPE);
                }
            }
        }

        Set<Long> touched = new LinkedHashSet<>();
        for (CurveEdge edge : c.edges()) {
            touched.add(edge.from());
            touched.add(edge.to());
        }
        touched.addAll(moved.keySet());
        for (long nodeId : touched) {
            Optional<PlaceError> error = checkNode(graph, c, moved, gone, nodeId);
            if (error.isPresent()) return error;
        }
        return Optional.empty();
    }

    private static Optional<PlaceError> checkNode(CurveGraph graph, PlacementCandidate c, Map<Long, CurveNode> moved,
                                                  Set<Long> gone, long nodeId) {
        List<CurveEdge> at = new ArrayList<>();
        for (CurveEdge edge : graph.edgesAt(nodeId)) if (!gone.contains(edge.id())) at.add(edge);
        for (CurveEdge edge : c.edges()) if (edge.from() == nodeId || edge.to() == nodeId) at.add(edge);
        if (at.isEmpty()) return Optional.empty();
        CurveNode node = node(graph, moved, nodeId);
        double[] diameters = new double[at.size()];
        for (int k = 0; k < at.size(); k++) diameters[k] = at.get(k).diameter();
        double joint = JointRules.jointRadius(diameters, node.forceJoint());
        for (CurveEdge edge : at) {
            long far = edge.from() == nodeId ? edge.to() : edge.from();
            if (JointRules.nodeInJoint(node.pos(), node(graph, moved, far).pos(), joint)) {
                return Optional.of(PlaceError.NODE_IN_JOINT);
            }
        }
        for (int i = 0; i < at.size(); i++) {
            for (int j = i + 1; j < at.size(); j++) {
                CurveEdge a = at.get(i), b = at.get(j);
                if (JointRules.angleTooSmall(CurveGraph.exitDirection(a, nodeId), CurveGraph.exitDirection(b, nodeId),
                        JointRules.pipeRadius(a.diameter()), JointRules.pipeRadius(b.diameter()), joint)) {
                    return Optional.of(PlaceError.ANGLE_TOO_SMALL);
                }
            }
        }
        return Optional.empty();
    }

    private static CurveNode node(CurveGraph graph, Map<Long, CurveNode> moved, long id) {
        CurveNode node = moved.get(id);
        if (node != null) return node;
        return graph.node(id).orElseThrow(() -> new IllegalArgumentException("unknown node " + id));
    }

    private static boolean sharesNode(CurveEdge a, CurveEdge b) {
        return a.from() == b.from() || a.from() == b.to() || a.to() == b.from() || a.to() == b.to();
    }

    private static boolean hitsBlock(Obb box, BlockShapes world) {
        Aabb bounds = box.bounds();
        for (int x = (int) Math.floor(bounds.minX()); x <= (int) Math.floor(bounds.maxX()); x++) {
            for (int y = (int) Math.floor(bounds.minY()); y <= (int) Math.floor(bounds.maxY()); y++) {
                for (int z = (int) Math.floor(bounds.minZ()); z <= (int) Math.floor(bounds.maxZ()); z++) {
                    for (Aabb solid : world.collision(x, y, z)) if (box.overlaps(solid)) return true;
                }
            }
        }
        return false;
    }
}
