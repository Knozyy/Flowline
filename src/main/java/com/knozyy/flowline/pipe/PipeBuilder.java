package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * "Build for me": with a pipe in the off hand, a key press lays pipes from the face of the block the player looks at
 * back to the player. The path goes around obstacles with as few turns as it can, and every pipe is placed the
 * normal way (so protection mods can refuse it and survival players pay for each one).
 */
public final class PipeBuilder {
    /** Search cost of one step and of changing direction: a turn costs half a step, so paths stay straight. */
    private static final int STEP = 2, TURN = 1;
    /** How far the search may leave the box spanned by the start and the player. */
    private static final int MARGIN = 4;
    private static final int MAX_VISITED = 200_000;

    /** A search state: where the path is and which way its last step went (6 = no step yet). */
    private record State(BlockPos pos, int dir) {}

    private PipeBuilder() {}

    public static void build(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()) return;
        ItemStack stack = player.getOffhandItem();
        if (!CurvyPipesCompat.pipe(stack)) {
            player.displayClientMessage(Component.translatable("message.flowline.build.no_pipe"), true);
            return;
        }
        int range = FlowlineConfig.BUILD_RANGE.get();
        HitResult hit = player.pick(range, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("message.flowline.build.nothing", range), true);
            return;
        }
        Level level = player.level();
        BlockPos start = blockHit.getBlockPos().relative(blockHit.getDirection());
        List<BlockPos> path = path(level, start, player.getBoundingBox(), range * 3);
        if (path == null) {
            player.displayClientMessage(Component.translatable("message.flowline.build.blocked"), true);
            return;
        }
        int placed = 0;
        for (BlockPos pos : path) {
            if (stack.isEmpty()) break;
            UseOnContext ctx = new UseOnContext(player, InteractionHand.OFF_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            // ItemStack#useOn runs Forge's place event, so claims and protection mods can refuse a pipe
            if (!stack.useOn(ctx).consumesAction() || !(level.getBlockState(pos).getBlock() instanceof PipeBlock)) break;
            placed++;
        }
        player.displayClientMessage(placed == path.size()
                ? Component.translatable("message.flowline.build.done", placed)
                : Component.translatable("message.flowline.build.partial", placed, path.size()), true);
    }

    /**
     * Free positions from {@code start} to next to the player, in order, or null if there is no such path within
     * {@code maxLength} pipes. Positions the player stands in are never used.
     */
    @Nullable
    public static List<BlockPos> path(Level level, BlockPos start, AABB player, int maxLength) {
        Set<BlockPos> body = new HashSet<>();
        BlockPos.betweenClosedStream(player.deflate(1e-4)).forEach(pos -> body.add(pos.immutable()));
        if (body.contains(start) || !free(level, start)) return null;
        Set<BlockPos> goals = new HashSet<>();
        for (BlockPos pos : body) {
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!body.contains(next)) goals.add(next);
            }
        }
        BlockPos feet = BlockPos.containing(player.getCenter().x, player.minY, player.getCenter().z);
        int minX = Math.min(start.getX(), feet.getX()) - MARGIN, maxX = Math.max(start.getX(), feet.getX()) + MARGIN;
        int minY = Math.min(start.getY(), feet.getY()) - MARGIN, maxY = Math.max(start.getY(), feet.getY()) + MARGIN;
        int minZ = Math.min(start.getZ(), feet.getZ()) - MARGIN, maxZ = Math.max(start.getZ(), feet.getZ()) + MARGIN;

        // A* over (position, direction of the last step): turning costs extra, so the path keeps to few straight runs
        record Node(State state, int cost, int steps, int estimate) {}
        Map<State, Integer> best = new HashMap<>();
        Map<State, State> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.estimate(), b.estimate()));
        State first = new State(start, 6);
        open.add(new Node(first, 0, 1, heuristic(start, feet)));
        best.put(first, 0);
        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_VISITED) {
            Node node = open.poll();
            State here = node.state();
            if (best.getOrDefault(here, Integer.MAX_VALUE) < node.cost()) continue;
            if (goals.contains(here.pos())) return trace(parent, here);
            if (node.steps() >= maxLength) continue;
            for (Direction dir : Direction.values()) {
                BlockPos next = here.pos().relative(dir);
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ) continue;
                if (body.contains(next) || !free(level, next)) continue;
                int d = dir.ordinal();
                int cost = node.cost() + STEP + (here.dir() != 6 && here.dir() != d ? TURN : 0);
                State there = new State(next, d);
                if (cost >= best.getOrDefault(there, Integer.MAX_VALUE)) continue;
                best.put(there, cost);
                parent.put(there, here);
                open.add(new Node(there, cost, node.steps() + 1, cost + heuristic(next, feet)));
            }
        }
        return null;
    }

    private static boolean free(Level level, BlockPos pos) {
        return level.isLoaded(pos) && !level.isOutsideBuildHeight(pos) && level.getBlockState(pos).canBeReplaced();
    }

    /** Steps to the player's feet, minus the one or two the goal ring saves; never more than the real cost. */
    private static int heuristic(BlockPos pos, BlockPos feet) {
        return Math.max(0, pos.distManhattan(feet) - 2) * STEP;
    }

    private static List<BlockPos> trace(Map<State, State> parent, State end) {
        List<BlockPos> path = new ArrayList<>();
        for (State s = end; s != null; s = parent.get(s)) path.add(s.pos());
        Collections.reverse(path);
        return path;
    }
}
