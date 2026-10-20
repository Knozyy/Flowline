package com.knozyy.flowline.pipe;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.BooleanSupplier;

/** One tick of an extracting end attached to a block, shared by block pipe sides and curved pipe ends. */
public final class SideTick {
    private SideTick() {}

    /**
     * Runs the end's extraction when its pacing allows. The caller only passes ends that extract and touch a block;
     * {@code signal} (the redstone signal at the end) is asked for only when the end's redstone mode needs it.
     */
    public static void tick(ServerLevel level, Port port, BooleanSupplier signal) {
        SideConfig cfg = port.cfg();
        if (cfg.redstone == RedstoneMode.PULSE) {
            if (cfg.pulsePending) {
                cfg.pulsePending = false;
                long pulsed = operate(level, port, cfg, 1);
                cfg.lastMoved = pulsed;
                cfg.lastBlocked = false;
                cfg.signalling = signalAfter(level, port, cfg, pulsed, false);
            }
            return;
        }
        if (cfg.sleeping) {
            if (cfg.graph != null && cfg.graph.valid()) return;
            cfg.sleeping = false;
            cfg.interval = Pacing.start(cfg.speedCount);
            cfg.cooldown = 1;
        }
        if (cfg.interval < 0) {
            // First run: stagger ends so pipes placed together do not all work on the same tick.
            cfg.interval = Pacing.start(cfg.speedCount);
            cfg.cooldown = 1 + level.random.nextInt(cfg.interval);
        }
        if (--cfg.cooldown > 0) return;

        long moved = 0;
        boolean blocked = false;
        if (cfg.redstone != RedstoneMode.IGNORED) blocked = !cfg.redstone.allows(signal.getAsBoolean());
        if (!blocked) {
            moved = operate(level, port, cfg, cfg.interval);
            if (moved < 0) {   // asleep
                cfg.lastMoved = moved;
                cfg.lastBlocked = false;
                cfg.signalling = signalAfter(level, port, cfg, moved, false);
                return;
            }
        }
        cfg.lastMoved = moved;
        cfg.lastBlocked = blocked;
        cfg.signalling = signalAfter(level, port, cfg, moved, blocked);
        cfg.interval = moved <= 0 ? Pacing.afterIdle(cfg.interval, cfg.speedCount)
                // energy flows every tick while there is work, like a cable: many sources only give one
                // tick's worth per call, so a slower pipe would get less out of them
                : port.type() == PipeType.ENERGY ? 1
                : Pacing.afterWork(cfg.interval, cfg.speedCount);
        cfg.cooldown = cfg.interval;
    }

    /** Whether an end's redstone output condition holds after an operation that moved {@code moved} (-1: asleep). */
    private static boolean signalAfter(ServerLevel level, Port port, SideConfig cfg, long moved, boolean blocked) {
        return switch (cfg.signal) {
            case OFF -> false;
            case MOVING -> moved > 0;
            case STUCK -> {
                if (blocked || moved > 0) yield false;
                if (cfg.sourceCaps == null) cfg.sourceCaps = Caps.create(port.type(), level, port.block(), port.access());
                yield port.type().hasWork(cfg.sourceCaps, cfg);
            }
        };
    }

    /**
     * One operation of an extracting end.
     *
     * @return amount moved, or -1 if there is no target and the end went to sleep
     */
    private static long operate(ServerLevel level, Port port, SideConfig cfg, int elapsed) {
        List<PipeNetwork.Target> targets = PipeNetwork.targets(level, port, cfg);
        if (targets.isEmpty()) {
            cfg.sleeping = true;
            return -1;
        }
        if (cfg.sourceCaps == null) cfg.sourceCaps = Caps.create(port.type(), level, port.block(), port.access());
        return port.type().transfer(level, port.block(), cfg.sourceCaps, cfg, targets, elapsed);
    }
}
