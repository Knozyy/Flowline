package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;

import java.util.List;

/**
 * Adaptive operation interval of an extracting side, in the spirit of AE2's tick rate modulation: a side starts at
 * {@link #start}, gets faster by {@code accelerationStep} after every operation that moved something (down to
 * {@link #min}), and slower by {@code slowdownStep} after every operation that moved nothing (up to
 * {@code maxIdleInterval}). All values come from the config and are read on every call.
 */
public final class Pacing {
    private Pacing() {}

    public static int min() {
        return FlowlineConfig.MIN_INTERVAL.get();
    }

    /** Interval a side starts at, and returns to after waking up. Speed upgrades lower it. */
    public static int start(int speedCount) {
        int start = FlowlineConfig.START_INTERVAL.get() - speedCount * FlowlineConfig.SPEED_REDUCTION.get();
        return Math.max(min(), start);
    }

    public static int maxIdle(int speedCount) {
        return Math.max(start(speedCount), FlowlineConfig.MAX_IDLE_INTERVAL.get());
    }

    /** Next interval after an operation that moved something. */
    public static int afterWork(int interval, int speedCount) {
        int start = start(speedCount);
        if (interval > start) return start;
        return Math.max(min(), interval - FlowlineConfig.ACCELERATION_STEP.get());
    }

    /** Next interval after an operation that moved nothing. */
    public static int afterIdle(int interval, int speedCount) {
        return Math.min(maxIdle(speedCount), interval + FlowlineConfig.SLOWDOWN_STEP.get());
    }

    /** Multiplier on the per-operation amount for {@code stackCount} Stack upgrades. */
    public static int stackMultiplier(int stackCount) {
        List<? extends Integer> multipliers = FlowlineConfig.STACK_MULTIPLIERS.get();
        if (multipliers.isEmpty()) return 1;
        return Math.max(1, multipliers.get(Math.min(stackCount, multipliers.size() - 1)));
    }
}
