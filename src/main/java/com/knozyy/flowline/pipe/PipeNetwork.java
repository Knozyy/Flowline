package com.knozyy.flowline.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Stateless graph search over connected pipes. Networks are recomputed on every transfer. */
public final class PipeNetwork {
    /** Hard cap so a huge network cannot stall the server thread. */
    public static final int MAX_PIPES = 512;

    private static final Random RANDOM = new Random();

    private PipeNetwork() {}

    /** An INSERT side of some pipe: transfer into the block at {@code pipePos.relative(side)}. */
    public record Target(BlockPos pipePos, Direction side, int distance) {
        public BlockPos endpointPos() {
            return pipePos.relative(side);
        }

        /** Direction from the endpoint towards the pipe, i.e. the face the endpoint is accessed from. */
        public Direction access() {
            return side.getOpposite();
        }
    }

    public static List<Target> collectTargets(ServerLevel level, BlockPos origin, Direction extractSide,
                                              PipeType type, SideConfig cfg) {
        List<Target> targets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        queue.add(origin);
        depth.add(0);
        visited.add(origin);

        while (!queue.isEmpty() && visited.size() <= MAX_PIPES) {
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
                    targets.add(new Target(pos, dir, dist));
                }
            }
        }
        return order(targets, cfg);
    }

    private static List<Target> order(List<Target> targets, SideConfig cfg) {
        // Deterministic base order so round-robin is stable between ticks.
        targets.sort(Comparator.comparingInt(Target::distance)
                .thenComparingLong(t -> t.pipePos().asLong())
                .thenComparingInt(t -> t.side().ordinal()));
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
