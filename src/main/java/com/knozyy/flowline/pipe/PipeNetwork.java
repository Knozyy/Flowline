package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Cached pipe networks. A {@link Graph} is one connected set of pipes of one type, built once by a flood fill and
 * shared by every extracting side in it. Changes (connections, modes, pipes placed, broken, loaded or unloaded) only
 * invalidate the graphs at and next to the changed position, so unrelated networks keep their caches. Target lists
 * are computed per extracting side from the graph's adjacency without touching the world again.
 */
public final class PipeNetwork {
    private static final Random RANDOM = new Random();
    /** Graph of every cached pipe position, per level. */
    private static final Map<Level, Map<BlockPos, Graph>> GRAPHS = new WeakHashMap<>();
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

    /** One connected pipe network. */
    public static final class Graph {
        final PipeType type;
        /** Pipe position -> bit mask of directions leading to another pipe of the graph. */
        final Map<BlockPos, Integer> links = new HashMap<>();
        final Map<BlockPos, PipeBlockEntity> pipes = new HashMap<>();
        /** Target lists per extracting side, keyed by {@link #key}. */
        final Map<Long, List<Target>> targets = new HashMap<>();
        /** Every extracting side of the graph; built on first use. */
        List<Source> sources;
        boolean valid = true;

        Graph(PipeType type) {
            this.type = type;
        }

        public boolean valid() {
            return valid;
        }

        public int size() {
            return pipes.size();
        }

        public boolean contains(BlockPos pos) {
            return pipes.containsKey(pos);
        }
    }

    public static long version() {
        return version;
    }

    private static Map<BlockPos, Graph> graphs(Level level) {
        return GRAPHS.computeIfAbsent(level, l -> new HashMap<>());
    }

    /** Call whenever something at {@code pos} changes that can affect networks there or next to it. */
    public static void invalidate(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        version++;
        Map<BlockPos, Graph> map = GRAPHS.get(level);
        if (map == null || map.isEmpty()) return;
        drop(map, map.get(pos));
        for (Direction dir : Direction.values()) drop(map, map.get(pos.relative(dir)));
    }

    /** Forget every cached network, e.g. when the config changes. */
    public static void invalidateAll() {
        version++;
        for (Map<BlockPos, Graph> map : GRAPHS.values()) {
            map.values().forEach(g -> g.valid = false);
            map.clear();
        }
    }

    private static void drop(Map<BlockPos, Graph> map, Graph graph) {
        if (graph == null || !graph.valid) return;
        graph.valid = false;
        for (BlockPos p : graph.pipes.keySet()) map.remove(p, graph);
    }

    /** The cached graph containing the pipe at {@code pos}, building it if needed. */
    public static Graph graphAt(ServerLevel level, BlockPos pos, PipeType type) {
        Map<BlockPos, Graph> map = graphs(level);
        Graph graph = map.get(pos);
        if (graph != null && graph.valid && graph.type == type) return graph;
        graph = build(level, pos, type);
        for (BlockPos p : graph.pipes.keySet()) {
            Graph old = map.put(p, graph);
            // a size-capped flood fill can overlap an older graph: that one is out of date now
            if (old != null && old != graph) drop(map, old);
        }
        for (BlockPos p : graph.pipes.keySet()) map.put(p, graph);
        return graph;
    }

    private static Graph build(ServerLevel level, BlockPos origin, PipeType type) {
        Graph graph = new Graph(type);
        int maxPipes = FlowlineConfig.MAX_NETWORK_SIZE.get();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (graph.pipes.containsKey(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PipeBlock pipe) || pipe.type() != type) continue;
            if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) continue;
            if (graph.pipes.size() >= maxPipes) break;
            graph.pipes.put(pos.immutable(), be);
            int mask = 0;
            for (Direction dir : Direction.values()) {
                if (state.getValue(PipeBlock.prop(dir)) != Conn.PIPE) continue;
                BlockPos next = pos.relative(dir);
                if (!level.isLoaded(next)) continue;
                mask |= 1 << dir.ordinal();
                if (!graph.pipes.containsKey(next)) queue.add(next);
            }
            graph.links.put(pos.immutable(), mask);
        }
        return graph;
    }

    /** Targets for an extracting end in distribution order; uses the end's graph while it is valid. */
    public static List<Target> targets(ServerLevel level, Port origin, SideConfig cfg) {
        if (cfg.graph == null || !cfg.graph.valid || cfg.cachedTargets == null) {
            PipeSidePort side = (PipeSidePort) origin;
            BlockPos pos = side.pipe().getBlockPos();
            cfg.graph = graphAt(level, pos, origin.type());
            cfg.cachedTargets = cfg.graph.targets.computeIfAbsent(origin.order(),
                    k -> scan(level, cfg.graph, pos, side.side(), origin.type()));
        }
        return order(new ArrayList<>(cfg.cachedTargets), cfg);
    }

    /** Every extracting side of the network the pipe at {@code pos} belongs to, in a stable order. */
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
            sources.sort(Comparator.comparingLong((Source s) -> s.port().order()));
            graph.sources = List.copyOf(sources);
        }
        return graph.sources;
    }

    /** Breadth-first search over the graph's links: distances and every inserting side. */
    private static List<Target> scan(ServerLevel level, Graph graph, BlockPos origin, Direction extractSide,
                                     PipeType type) {
        List<Target> targets = new ArrayList<>();
        Map<BlockPos, Integer> depth = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        queue.add(start);
        depth.put(start, 0);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int dist = depth.get(pos);
            PipeBlockEntity be = graph.pipes.get(pos);
            if (be == null) continue;
            int mask = graph.links.getOrDefault(pos, 0);
            BlockState state = be.getBlockState();
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) {
                    BlockPos next = pos.relative(dir);
                    if (graph.pipes.containsKey(next) && !depth.containsKey(next)) {
                        depth.put(next, dist + 1);
                        queue.add(next);
                    }
                } else if (state.getValue(PipeBlock.prop(dir)).isEndpoint()
                        && be.side(dir).mode == SideMode.INSERT
                        && !(pos.equals(start) && dir == extractSide)) {
                    targets.add(new Target(new PipeSidePort(be, dir), dist,
                            Caps.create(type, level, pos.relative(dir), dir.getOpposite())));
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
