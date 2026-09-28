package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Graph search over connected pipes. Extracting sides cache their target lists; any change that can alter a
 * network (connections, modes, pipes added or removed) bumps a global {@link #version()} which invalidates every
 * cache and wakes sleeping sides.
 */
public final class PipeNetwork {
    private static final Random RANDOM = new Random();
    private static long version = 0;

    private PipeNetwork() {}

    /**
     * An INSERT side of some pipe: transfer into the block at {@code pipePos.relative(side)}. Holds a NeoForge
     * capability cache for that block, so transfers do not look the block entity up again on every operation.
     */
    public record Target(BlockPos pipePos, Direction side, int distance, BlockCapabilityCache<?, Direction> cache) {
        public BlockPos endpointPos() {
            return pipePos.relative(side);
        }

        /** Direction from the endpoint towards the pipe, i.e. the face the endpoint is accessed from. */
        public Direction access() {
            return side.getOpposite();
        }
    }

    public static long version() {
        return version;
    }

    /** Call whenever something that affects target lists changes. */
    public static void invalidate() {
        version++;
    }

    /** Targets for an extracting side in distribution order; uses the side's cache when still valid. */
    public static List<Target> targets(ServerLevel level, BlockPos origin, Direction extractSide, PipeType type,
                                       SideConfig cfg) {
        if (cfg.cachedTargets == null || cfg.cachedVersion != version) {
            cfg.cachedTargets = List.copyOf(scan(level, origin, extractSide, type));
            cfg.cachedVersion = version;
        }
        return order(new ArrayList<>(cfg.cachedTargets), cfg);
    }

    private static List<Target> scan(ServerLevel level, BlockPos origin, Direction extractSide, PipeType type) {
        List<Target> targets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        int maxPipes = FlowlineConfig.MAX_NETWORK_SIZE.get();
        queue.add(origin);
        depth.add(0);
        visited.add(origin);

        while (!queue.isEmpty() && visited.size() <= maxPipes) {
            BlockPos pos = queue.poll();
            int dist = depth.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PipeBlock pipe) || pipe.type() != type) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof PipeBlockEntity pipeBe)) continue;

            for (Direction dir : Direction.values()) {
                Conn conn = state.getValue(PipeBlock.prop(dir));
                if (conn == Conn.PIPE) {
                    BlockPos next = pos.relative(dir);
                    if (level.isLoaded(next) && visited.add(next)) {
                        queue.add(next);
                        depth.add(dist + 1);
                    }
                } else if (conn == Conn.ENDPOINT
                        && pipeBe.side(dir).mode == SideMode.INSERT
                        && !(pos.equals(origin) && dir == extractSide)) {
                    targets.add(new Target(pos, dir, dist,
                            BlockCapabilityCache.create(type.capability(), level, pos.relative(dir), dir.getOpposite())));
                }
            }
        }
        // Deterministic base order so round-robin is stable between operations.
        targets.sort(Comparator.comparingInt(Target::distance)
                .thenComparingLong(t -> t.pipePos().asLong())
                .thenComparingInt(t -> t.side().ordinal()));
        return targets;
    }

    private static List<Target> order(List<Target> targets, SideConfig cfg) {
        switch (cfg.distribution) {
            case NEAREST -> {}
            case FARTHEST -> java.util.Collections.reverse(targets);
            case RANDOM -> java.util.Collections.shuffle(targets, RANDOM);
            case ROUND_ROBIN -> {
                if (!targets.isEmpty()) {
                    java.util.Collections.rotate(targets, -Math.floorMod(cfg.roundRobin, targets.size()));
                    cfg.roundRobin++;
                }
            }
        }
        return targets;
    }
}
