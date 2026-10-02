package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NetworkView {
    public static final int PIPE = 1, SOURCE = 2, TARGET = 4, OVERFLOW = 8;
    public static final int MAX_ENTRIES = 2048;

    public record Entry(BlockPos pos, int roles, int priority) {
        public boolean has(int role) { return (roles & role) != 0; }
    }

    public record Snapshot(List<Entry> entries, boolean truncated) {
        public Snapshot { entries = List.copyOf(entries); }
    }

    private NetworkView() {}

    public static Snapshot query(ServerPlayer player, BlockPos origin) {
        int range = FlowlineConfig.NETWORK_VIEW_RANGE.get();
        if (!FlowlineConfig.ALLOW_NETWORK_VIEW.get() || !player.isShiftKeyDown()
                || !WrenchItem.isWrench(player.getMainHandItem()) || !player.serverLevel().isLoaded(origin)
                || player.distanceToSqr(Vec3.atCenterOf(origin)) > (double) range * range) {
            return new Snapshot(List.of(), false);
        }
        HitResult looked = player.pick(range, 1, false);
        if (!(looked instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(origin)) return new Snapshot(List.of(), false);
        return collect(player.serverLevel(), origin, player.position(), range);
    }

    public static Snapshot collect(ServerLevel level, BlockPos origin, Vec3 viewer, int range) {
        if (!level.isLoaded(origin) || !(level.getBlockEntity(origin) instanceof PipeBlockEntity pipe)) {
            return new Snapshot(List.of(), false);
        }
        PipeNetwork.Graph graph = PipeNetwork.graphAt(level, origin, pipe.type());
        Map<BlockPos, Entry> entries = new LinkedHashMap<>();
        boolean truncated = false;
        List<BlockPos> positions = graph.pipes.keySet().stream()
                .sorted(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(viewer))).toList();
        for (BlockPos pos : positions) {
            if (Vec3.atCenterOf(pos).distanceToSqr(viewer) > (double) range * range) continue;
            PipeBlockEntity be = graph.pipes.get(pos);
            if (!level.isLoaded(pos) || be.isRemoved()) continue;
            truncated |= !add(entries, pos, PIPE, 0);
            for (Direction side : Direction.values()) {
                SideConfig cfg = be.side(side);
                if (!be.getBlockState().getValue(PipeBlock.prop(side)).isEndpoint()) continue;
                if (be.type().hasChannels() && cfg.channels == 0) continue;
                BlockPos endpoint = pos.relative(side);
                if (!level.isLoaded(endpoint) || Vec3.atCenterOf(endpoint).distanceToSqr(viewer) > (double) range * range) continue;
                int role = cfg.mode == SideMode.EXTRACT ? SOURCE : cfg.overflow ? OVERFLOW : TARGET;
                truncated |= !add(entries, endpoint, role, cfg.priority);
            }
        }
        return new Snapshot(List.copyOf(entries.values()), truncated);
    }

    private static boolean add(Map<BlockPos, Entry> entries, BlockPos pos, int role, int priority) {
        Entry old = entries.get(pos);
        if (old == null && entries.size() >= MAX_ENTRIES) return false;
        entries.put(pos.immutable(), old == null ? new Entry(pos.immutable(), role, priority)
                : new Entry(pos.immutable(), old.roles() | role, Math.max(old.priority(), priority)));
        return true;
    }
}
