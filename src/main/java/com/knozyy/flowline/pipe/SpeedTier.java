package com.knozyy.flowline.pipe;

/** Upgrade installed on a side. {@link #BASE} means none; any other tier also unlocks the filter. */
public enum SpeedTier {
    BASE(20, 1),
    BASIC(10, 2),
    REGULAR(5, 4),
    ADVANCED(2, 8),
    KNOZY(1, 16);

    /** Ticks between two transfer operations. */
    public final int interval;
    /** Multiplier applied to the per-operation base amount. */
    public final int multiplier;

    SpeedTier(int interval, int multiplier) {
        this.interval = interval;
        this.multiplier = multiplier;
    }

    public boolean isUpgraded() {
        return this != BASE;
    }

    public static SpeedTier byIndex(int i) {
        SpeedTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
