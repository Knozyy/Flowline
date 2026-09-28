package com.knozyy.flowline.pipe;

/** Speed upgrade level of an extracting side. Tier 0 means no upgrade installed. */
public enum SpeedTier {
    BASE(20, 1),
    TIER_1(10, 2),
    TIER_2(5, 4),
    TIER_3(2, 8);

    /** Ticks between two transfer operations. */
    public final int interval;
    /** Multiplier applied to the per-operation base amount. */
    public final int multiplier;

    SpeedTier(int interval, int multiplier) {
        this.interval = interval;
        this.multiplier = multiplier;
    }

    public static SpeedTier byIndex(int i) {
        SpeedTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
