package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.curved.CurvedEnd;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Cached pipe networks. A {@link Graph} is one connected set of pipes of one type (block pipes and curved pipes),
 * built once by a flood fill and shared by every extracting end in it. Changes (connections, modes, pipes placed, broken, loaded or unloaded) only
 * invalidate the graphs at and next to the changed position, so unrelated networks keep their caches. Target lists
 * are computed per extracting side from the graph's adjacency without touching the world again.
 */
public final class PipeNetwork {
    private static final Random RANDOM = new Random();
    /** Graph of every cached pipe position, per level. */
    private static final Map<Level, Map<BlockPos, Graph>> GRAPHS = new WeakHashMap<>();
    /** Graph of every cached curved node, per level. */
    private static final Map<Level, Map<Long, Graph>> NODE_GRAPHS = new WeakHashMap<>();
    /** Bumped on every invalidation; only used by tests and debugging. */
    private static long version = 0;

    private PipeNetwork() {}

    /**
     * An INSERT end of the network: transfer into {@code port.block()}. Holds a Forge capability cache for that block,
     * so transfers do not look the block entity up again on every operation.
     */
    public record Target(Port port, int distance, Caps caps) {
        public BlockPos endpointPos() {
            return port.block();
        }

        /** The face the endpoint is accessed from. */
        public Direction access() {
            return port.access();
        }

        /** Configuration of the inserting end: its insert filter, priority and limit. */
        public SideConfig insert() {
            return port.cfg();
        }
    }

    /** An EXTRACT end of the network: it pulls from {@code port.block()} through {@code caps}. */
    public record Source(Port port, Caps caps) {
        public SideConfig cfg() {
            return port.cfg();
        }
    }

    /** A vertex of a network: a block pipe, or a curved pipe node. */
    public sealed interface Vertex permits PipeVertex, NodeVertex {}

    public record PipeVertex(BlockPos pos) implements Vertex {}

    public record NodeVertex(long id) implements Vertex {}

    /** A step from one vertex to another; a curved edge weighs its rounded length. */
    private record Link(Vertex to, int weight) {}

    /** One connected pipe network: block pipes and curved nodes of one type. */
    public static final class Graph {
        final PipeType type;
        final Map<BlockPos, PipeBlockEntity> pipes = new HashMap<>();
        final Set<Long> nodes = new HashSet<>();
        /** Curved ends of the graph that touch a block holding what the type carries. */
        final Map<Long, CurvedEnd> ends = new HashMap<>();
        final Map<Vertex, List<Link>> links = new HashMap<>();
        /** Target lists per extracting end, keyed by {@link Port#order()}. */
        final Map<Long, List<Target>> targets = new HashMap<>();
        /** Every extracting end of the graph; built on first use. */
        List<Source> sources;
        boolean valid = true;

        Graph(PipeType type) {
            this.type = type;
        }

        public boolean valid() {
            return valid;
        }

        /** Block pipes and curved nodes in the graph. */
        public int size() {
            return pipes.size() + nodes.size();
        }

        public boolean contains(BlockPos pos) {
            return pipes.containsKey(pos);
        }

        public boolean containsNode(long id) {
            return nodes.contains(id);
        }

        void link(Vertex from, Vertex to, int weight) {
            links.computeIfAbsent(from, k -> new ArrayList<>()).add(new Link(to, weight));
        }
    }

    public static long version() {
        return version;
    }

    private static Map<BlockPos, Graph> graphs(Level level) {
        return GRAPHS.computeIfAbsent(level, l -> new HashMap<>());
    }

    private static Map<Long, Graph> nodeGraphs(Level level) {
        return NODE_GRAPHS.computeIfAbsent(level, l -> new HashMap<>());
    }

    /** Call whenever something at {@code pos} changes that can affect networks there or next to it. */
    public static void invalidate(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        version++;
        Map<BlockPos, Graph> map = GRAPHS.get(level);
        if (map == null || map.isEmpty()) return;
        drop(level, map.get(pos));
        for (Direction dir : Direction.values()) drop(level, map.get(pos.relative(dir)));
    }

    /** Call whenever curved node {@code id}, its edges or its end change. */
    public static void invalidateNode(Level level, long id) {
        if (level.isClientSide) return;
        version++;
        Map<Long, Graph> map = NODE_GRAPHS.get(level);
        if (map != null) drop(level, map.get(id));
    }

    /** Forget every cached network, e.g. when the config changes. */
    public static void invalidateAll() {
        version++;
        for (Map<BlockPos, Graph> map : GRAPHS.values()) {
            map.values().forEach(g -> g.valid = false);
            map.clear();
        }
        for (Map<Long, Graph> map : NODE_GRAPHS.values()) {
            map.values().forEach(g -> g.valid = false);
            map.clear();
        }
    }

    private static void drop(Level level, Graph graph) {
        if (graph == null || !graph.valid) return;
        graph.valid = false;
        Map<BlockPos, Graph> pipes = graphs(level);
        for (BlockPos p : graph.pipes.keySet()) pipes.remove(p, graph);
        Map<Long, Graph> nodes = nodeGraphs(level);
        for (long n : graph.nodes) nodes.remove(n, graph);
    }

    /** The cached graph containing the pipe at {@code pos}, building it if needed. */
    public static Graph graphAt(ServerLevel level, BlockPos pos, PipeType type) {
        return graphAt(level, new PipeVertex(pos), type);
    }

    private static Graph graphAt(ServerLevel level, Vertex origin, PipeType type) {
        Graph graph = origin instanceof PipeVertex p ? graphs(level).get(p.pos())
                : nodeGraphs(level).get(((NodeVertex) origin).id());
        if (graph != null && graph.valid && graph.type == type) return graph;
        graph = build(level, origin, type);
        Map<BlockPos, Graph> pipes = graphs(level);
        Map<Long, Graph> nodes = nodeGraphs(level);
        // a size-capped flood fill can overlap an older graph: that one is out of date now
        for (BlockPos p : graph.pipes.keySet()) {
            Graph old = pipes.get(p);
            if (old != null && old != graph) drop(level, old);
        }
        for (long n : graph.nodes) {
            Graph old = nodes.get(n);
            if (old != null && old != graph) drop(level, old);
        }
        for (BlockPos p : graph.pipes.keySet()) pipes.put(p, graph);
        for (long n : graph.nodes) nodes.put(n, graph);
        return graph;
    }

    private static Vertex vertexOf(Port port) {
        if (port instanceof PipeSidePort side) return new PipeVertex(side.pipe().getBlockPos());
        if (port instanceof CurvedEnd end) return new NodeVertex(end.nodeId());
        throw new IllegalArgumentException("unknown port " + port);
    }

    private static boolean loaded(ServerLevel level, Vec3d p) {
        return level.isLoaded(BlockPos.containing(p.x(), p.y(), p.z()));
    }

    /**
     * Flood fill from {@code origin}: block pipes through their pipe connections, a block pipe face with a curved node
     * on it to that node, curved nodes along their edges of the same type, and a curved node on a block pipe face back
     * to that pipe. Curved nodes in unloaded chunks are left out, like unloaded block pipes.
     */
    private static Graph build(ServerLevel level, Vertex origin, PipeType type) {
        Graph graph = new Graph(type);
        CurveData curves = CurveData.get(level);
        int maxSize = FlowlineConfig.MAX_NETWORK_SIZE.get();
        Set<Vertex> seen = new HashSet<>();
        ArrayDeque<Vertex> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            Vertex vertex = queue.poll();
            if (!seen.add(vertex)) continue;
            if (graph.size() >= maxSize) break;
            if (vertex instanceof PipeVertex pv) {
                BlockPos pos = pv.pos();
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof PipeBlock pipe) || pipe.type() != type) continue;
                if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) continue;
                graph.pipes.put(pos.immutable(), be);
                for (Direction dir : Direction.values()) {
                    if (state.getValue(PipeBlock.prop(dir)) != Conn.PIPE) continue;
                    OptionalLong node = curves.nodeAtPipeFace(pos, dir);
                    Vertex next;
                    if (node.isPresent()) {
                        next = new NodeVertex(node.getAsLong());
                    } else {
                        BlockPos neighbour = pos.relative(dir);
                        if (!level.isLoaded(neighbour)) continue;
                        next = new PipeVertex(neighbour.immutable());
                    }
                    graph.link(vertex, next, 1);
                    queue.add(next);
                }
            } else {
                long id = ((NodeVertex) vertex).id();
                CurveNode node = curves.graph().node(id).orElse(null);
                if (node == null || curves.nodeType(id) != type || !loaded(level, node.pos())) continue;
                graph.nodes.add(id);
                Anchor anchor = curves.anchor(id);
                if (anchor instanceof Anchor.PipeFace face && level.isLoaded(face.pos())) {
                    BlockState state = level.getBlockState(face.pos());
                    if (state.getBlock() instanceof PipeBlock pipe && pipe.type() == type
                            && state.getValue(PipeBlock.prop(face.face())) == Conn.PIPE) {
                        Vertex next = new PipeVertex(face.pos());
                        graph.link(vertex, next, 1);
                        queue.add(next);
                    }
                } else if (anchor instanceof Anchor.BlockFace face) {
                    CurvedEnd end = curves.end(id).orElse(null);
                    if (end != null && level.isLoaded(face.pos()) && type.hasEndpoint(level, face.pos(), face.face())) {
                        graph.ends.put(id, end);
                    }
                }
                for (CurveEdge edge : curves.graph().edgesAt(id)) {
                    if (curves.info(edge.id()).type() != type) continue;
                    long other = edge.from() == id ? edge.to() : edge.from();
                    CurveNode far = curves.graph().node(other).orElse(null);
                    if (far == null || !loaded(level, far.pos())) continue;
                    Vertex next = new NodeVertex(other);
                    graph.link(vertex, next, Math.max(1, edge.shape().cost()));
                    queue.add(next);
                }
            }
        }
        return graph;
    }

    /** Targets for an extracting end in distribution order; uses the end's graph while it is valid. */
    public static List<Target> targets(ServerLevel level, Port origin, SideConfig cfg) {
        if (cfg.graph == null || !cfg.graph.valid || cfg.cachedTargets == null) {
            cfg.graph = graphAt(level, vertexOf(origin), origin.type());
            cfg.cachedTargets = cfg.graph.targets.computeIfAbsent(origin.order(), k -> scan(level, cfg.graph, origin));
        }
        return order(new ArrayList<>(cfg.cachedTargets), cfg);
    }

    /** Every extracting end of the network the pipe at {@code pos} belongs to, in a stable order. */
    public static List<Source> sources(ServerLevel level, BlockPos pos, PipeType type) {
        Graph graph = graphAt(level, pos, type);
        if (graph.sources == null) {
            List<Source> sources = new ArrayList<>();
            for (Map.Entry<BlockPos, PipeBlockEntity> entry : graph.pipes.entrySet()) {
                BlockPos pipePos = entry.getKey();
                PipeBlockEntity be = entry.getValue();
                BlockState state = be.getBlockState();
                for (Direction dir : Direction.values()) {
                    if (state.getValue(PipeBlock.prop(dir)).isEndpoint() && be.side(dir).mode == SideMode.EXTRACT) {
                        sources.add(new Source(new PipeSidePort(be, dir),
                                Caps.create(type, level, pipePos.relative(dir), dir.getOpposite())));
                    }
                }
            }
            for (CurvedEnd end : graph.ends.values()) {
                if (end.cfg().mode == SideMode.EXTRACT) {
                    sources.add(new Source(end, Caps.create(type, level, end.block(), end.access())));
                }
            }
            sources.sort(Comparator.comparingLong((Source s) -> s.port().order()));
            graph.sources = List.copyOf(sources);
        }
        return graph.sources;
    }

    private record Visit(Vertex vertex, int distance, long sequence) {}

    /** Shortest distances over the graph's links (Dijkstra) and every inserting end, nearest first. */
    private static List<Target> scan(ServerLevel level, Graph graph, Port origin) {
        PipeType type = graph.type;
        List<Target> targets = new ArrayList<>();
        Map<Vertex, Integer> best = new HashMap<>();
        Set<Vertex> done = new HashSet<>();
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingInt(Visit::distance)
                .thenComparingLong(Visit::sequence));
        long sequence = 0;
        Vertex start = vertexOf(origin);
        queue.add(new Visit(start, 0, sequence++));
        best.put(start, 0);
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            Vertex vertex = visit.vertex();
            if (!done.add(vertex)) continue;
            int dist = visit.distance();
            if (vertex instanceof PipeVertex pv) {
                BlockPos pos = pv.pos();
                PipeBlockEntity be = graph.pipes.get(pos);
                if (be == null) continue;
                BlockState state = be.getBlockState();
                for (Direction dir : Direction.values()) {
                    if (!state.getValue(PipeBlock.prop(dir)).isEndpoint() || be.side(dir).mode != SideMode.INSERT) continue;
                    if (origin instanceof PipeSidePort side && side.pipe() == be && side.side() == dir) continue;
                    targets.add(new Target(new PipeSidePort(be, dir), dist,
                            Caps.create(type, level, pos.relative(dir), dir.getOpposite())));
                }
            } else {
                CurvedEnd end = graph.ends.get(((NodeVertex) vertex).id());
                if (end != null && end != origin && end.cfg().mode == SideMode.INSERT) {
                    targets.add(new Target(end, dist, Caps.create(type, level, end.block(), end.access())));
                }
            }
            for (Link link : graph.links.getOrDefault(vertex, List.of())) {
                int next = dist + link.weight();
                if (next < best.getOrDefault(link.to(), Integer.MAX_VALUE)) {
                    best.put(link.to(), next);
                    queue.add(new Visit(link.to(), next, sequence++));
                }
            }
        }
        // Deterministic base order so round-robin is stable between operations.
        targets.sort(Comparator.comparingInt(Target::distance).thenComparingLong(t -> t.port().order()));
        return List.copyOf(targets);
    }

    private static List<Target> order(List<Target> targets, SideConfig cfg) {
        switch (cfg.distribution) {
            case NEAREST -> {}
            case FARTHEST -> Collections.reverse(targets);
            case RANDOM -> Collections.shuffle(targets, RANDOM);
            case ROUND_ROBIN, BALANCED -> {
                if (!targets.isEmpty()) {
                    Collections.rotate(targets, -Math.floorMod(cfg.roundRobin, targets.size()));
                    cfg.roundRobin++;
                }
            }
            // stable: equal priorities keep the nearest-first base order
            case PRIORITY -> targets.sort(Comparator.comparingInt((Target t) -> t.insert().priority).reversed());
        }
        return targets;
    }
}
