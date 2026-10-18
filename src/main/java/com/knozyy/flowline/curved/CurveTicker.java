package com.knozyy.flowline.curved;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SideTick;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runs the extracting curved ends of every server level once per tick, like a block pipe's tick runs its sides. An
 * end works when its node's chunk is loaded and its block holds what the pipe carries. Its redstone signal is the one
 * at the node's cell, as for a block pipe standing there.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class CurveTicker {
    private CurveTicker() {}

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        CurveData data = CurveData.get(level);
        data.processDirty(level);
        for (CurvedEnd end : new java.util.ArrayList<>(data.ends())) tick(level, data, end);
        CurveSync.flush(level);
    }

    private static void tick(ServerLevel level, CurveData data, CurvedEnd end) {
        SideConfig cfg = end.cfg();
        CurveNode node = data.graph().node(end.nodeId()).orElse(null);
        BlockPos cell = node == null ? null : BlockPos.containing(node.pos().x(), node.pos().y(), node.pos().z());
        boolean works = cfg.mode == SideMode.EXTRACT && cell != null && level.isLoaded(cell)
                && level.isLoaded(end.block()) && end.type().hasEndpoint(level, end.block(), end.access());
        if (!works) {
            if (cfg.interval >= 0 || cfg.graph != null) cfg.resetRuntime();
            cfg.signalling = false;
            return;
        }
        if (cfg.redstone == RedstoneMode.PULSE) {
            boolean signal = level.hasNeighborSignal(cell);
            if (signal && !end.powered) cfg.pulsePending = true;
            end.powered = signal;
        }
        SideTick.tick(level, end, () -> level.hasNeighborSignal(cell));
    }
}
