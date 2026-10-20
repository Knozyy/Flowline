package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The curved pipes of one world as plain geometry: nodes, solved edges, their segment boxes and a block-cell index for
 * volume and ray queries. Saving, networking and edit journals live outside this package.
 */
public final class CurveGraph {
    private record Ref(long edge, int segment) {}

    private final Map<Long, CurveNode> nodes = new LinkedHashMap<>();
    private final Map<Long, CurveEdge> edges = new LinkedHashMap<>();
    private final Map<Long, List<Obb>> boxes = new HashMap<>();
    private final Map<Long, Set<Long>> edgesAt = new HashMap<>();
    private final Map<Long, Set<Ref>> cells = new HashMap<>();

    public void putNode(CurveNode node) {
        nodes.put(node.id(), node);
    }

    /** Removes a node that has no edges left. */
    public void removeNode(long id) {
        if (!edgesAt.getOrDefault(id, Set.of()).isEmpty()) {
            throw new IllegalStateException("node " + id + " still has edges");
        }
        nodes.remove(id);
        edgesAt.remove(id);
    }

    /** Adds the edge, or replaces the one with the same id. Both nodes must already be in the graph. */
    public void putEdge(CurveEdge edge) {
        if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to())) {
            throw new IllegalArgumentException("edge " + edge.id() + " joins a node that is not in the graph");
        }
        if (edges.containsKey(edge.id())) removeEdge(edge.id());
        List<Obb> shape = EdgeShape.boxes(edge.shape(), 0.5 * edge.diameter());
        edges.put(edge.id(), edge);
        boxes.put(edge.id(), shape);
        edgesAt.computeIfAbsent(edge.from(), k -> new LinkedHashSet<>()).add(edge.id());
        edgesAt.computeIfAbsent(edge.to(), k -> new LinkedHashSet<>()).add(edge.id());
        for (int k = 0; k < shape.size(); k++) {
            Ref ref = new Ref(edge.id(), k);
            forEachCell(shape.get(k).bounds(), key -> cells.computeIfAbsent(key, x -> new LinkedHashSet<>()).add(ref));
        }
    }

    public void removeEdge(long id) {
        CurveEdge edge = edges.remove(id);
        if (edge == null) return;
        List<Obb> shape = boxes.remove(id);
        edgesAt.get(edge.from()).remove(id);
        edgesAt.get(edge.to()).remove(id);
        for (int k = 0; k < shape.size(); k++) {
            Ref ref = new Ref(id, k);
            forEachCell(shape.get(k).bounds(), key -> {
                Set<Ref> refs = cells.get(key);
                if (refs != null && refs.remove(ref) && refs.isEmpty()) cells.remove(key);
            });
        }
    }

    /** Every node, in the order they were first added. */
    public java.util.Collection<CurveNode> nodes() {
        return java.util.Collections.unmodifiableCollection(nodes.values());
    }

    /** Every edge, in the order they were first added. */
    public java.util.Collection<CurveEdge> edges() {
        return java.util.Collections.unmodifiableCollection(edges.values());
    }

    public Optional<CurveNode> node(long id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Optional<CurveEdge> edge(long id) {
        return Optional.ofNullable(edges.get(id));
    }

    public List<CurveEdge> edgesAt(long nodeId) {
        List<CurveEdge> list = new ArrayList<>();
        for (long id : edgesAt.getOrDefault(nodeId, Set.of())) list.add(edges.get(id));
        return list;
    }

    public List<Obb> boxes(long edgeId) {
        return boxes.getOrDefault(edgeId, List.of());
    }

    /** Every segment box whose bounds meet {@code area}, each once. */
    public List<SegmentBox> boxesNear(Aabb area) {
        Set<Ref> found = new LinkedHashSet<>();
        forEachCell(area, key -> found.addAll(cells.getOrDefault(key, Set.of())));
        List<SegmentBox> list = new ArrayList<>();
        for (Ref ref : found) {
            Obb box = boxes.get(ref.edge()).get(ref.segment());
            if (box.bounds().intersects(area)) list.add(new SegmentBox(ref.edge(), ref.segment(), box));
        }
        return list;
    }

    /** The nearest edge along the unit ray within {@code maxDist}. */
    public Optional<RayHit> raycast(Vec3d origin, Vec3d dir, double maxDist) {
        RayHit best = null;
        for (SegmentBox candidate : boxesNear(Aabb.of(origin, origin.addScaled(dir, maxDist)))) {
            double t = candidate.box().rayHit(origin, dir, maxDist);
            if (Double.isNaN(t) || best != null && t >= best.distance()) continue;
            Obb box = candidate.box();
            Vec3d point = origin.addScaled(dir, t);
            double along = (point.sub(box.center()).dot(box.u()) + box.hu()) / (2 * box.hu());
            best = new RayHit(candidate.edgeId(), candidate.segment(), t, point, Math.max(0, Math.min(1, along)));
        }
        return Optional.ofNullable(best);
    }

    /** The unit direction in which {@code edge} leaves node {@code nodeId}. */
    public static Vec3d exitDirection(CurveEdge edge, long nodeId) {
        List<Vec3d> p = edge.shape().points();
        if (edge.from() == nodeId) return p.get(1).sub(p.get(0)).normalize();
        if (edge.to() == nodeId) return p.get(p.size() - 2).sub(p.get(p.size() - 1)).normalize();
        throw new IllegalArgumentException("edge " + edge.id() + " does not touch node " + nodeId);
    }

    private static void forEachCell(Aabb box, java.util.function.LongConsumer action) {
        int x0 = (int) Math.floor(box.minX()), x1 = (int) Math.floor(box.maxX());
        int y0 = (int) Math.floor(box.minY()), y1 = (int) Math.floor(box.maxY());
        int z0 = (int) Math.floor(box.minZ()), z1 = (int) Math.floor(box.maxZ());
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) action.accept(cell(x, y, z));
            }
        }
    }

    /** A block cell as one long: 21 bits per coordinate. */
    private static long cell(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
