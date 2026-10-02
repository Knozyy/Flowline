package com.knozyy.flowline.pipe;

import java.util.function.BooleanSupplier;

/** What an extracting side is doing right now, as the pipe screen's badge shows it. */
public enum SideStatus {
    /** The last operation moved something. */
    WORKING,
    /** No target in the network. */
    SLEEPING,
    /** The redstone mode holds the side back, or a pulse is awaited. */
    WAITING,
    /** The source has something for this side but nothing could be delivered. */
    STUCK,
    /** Nothing to move in the source. */
    IDLE,
    /** The side has not run since it started extracting. */
    STARTING;

    /**
     * @param hasWork whether the source holds something this side would move; only asked when the rest cannot
     *                decide, because it scans the source
     */
    public static SideStatus of(SideConfig cfg, BooleanSupplier hasWork) {
        if (cfg.sleeping) return SLEEPING;
        if (cfg.redstone == RedstoneMode.PULSE) return cfg.pulsePending ? WORKING : WAITING;
        if (cfg.lastBlocked) return WAITING;
        if (cfg.lastMoved == SideConfig.NOT_RUN) return STARTING;
        if (cfg.lastMoved > 0) return WORKING;
        return hasWork.getAsBoolean() ? STUCK : IDLE;
    }

    public static SideStatus byOrdinal(int ordinal) {
        SideStatus[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }
}
