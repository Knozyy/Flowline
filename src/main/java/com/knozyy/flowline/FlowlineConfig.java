package com.knozyy.flowline;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Common config. Values are read at runtime, never cached, so edits apply without a restart. */
public final class FlowlineConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Amount moved per operation by an extracting side, before Stack upgrades.").push("amounts");
    }

    public static final ModConfigSpec.IntValue ITEMS_PER_OPERATION = BUILDER
            .defineInRange("itemsPerOperation", 1, 1, 4096);

    public static final ModConfigSpec.IntValue FLUID_PER_OPERATION = BUILDER
            .comment("Millibuckets.")
            .defineInRange("fluidPerOperation", 100, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ENERGY_PER_OPERATION = BUILDER
            .comment("FE.")
            .defineInRange("energyPerOperation", 1000, 1, 100_000_000);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on the amounts above, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "Entry 0 is used without upgrades; the last entry is used for any higher count.")
            .defineList("stackMultipliers", List.of(1, 8, 16, 32, 64, 96, 128),
                    o -> o instanceof Integer i && i >= 1);

    static {
        BUILDER.pop();
        BUILDER.comment("Adaptive operation interval (ticks). A side starts at startInterval, speeds up by",
                "accelerationStep after each operation that moved something (down to minInterval) and slows",
                "down by slowdownStep after each one that moved nothing (up to maxIdleInterval).",
                "Sides without any target sleep until the pipe network changes.").push("pacing");
    }

    public static final ModConfigSpec.IntValue START_INTERVAL = BUILDER
            .defineInRange("startInterval", 30, 1, 1200);

    public static final ModConfigSpec.IntValue MIN_INTERVAL = BUILDER
            .defineInRange("minInterval", 5, 1, 1200);

    public static final ModConfigSpec.IntValue MAX_IDLE_INTERVAL = BUILDER
            .defineInRange("maxIdleInterval", 100, 1, 12000);

    public static final ModConfigSpec.IntValue SPEED_REDUCTION = BUILDER
            .comment("Ticks each Speed upgrade (or Knozy) removes from startInterval.")
            .defineInRange("speedReduction", 4, 0, 1200);

    public static final ModConfigSpec.IntValue ACCELERATION_STEP = BUILDER
            .defineInRange("accelerationStep", 2, 0, 1200);

    public static final ModConfigSpec.IntValue SLOWDOWN_STEP = BUILDER
            .defineInRange("slowdownStep", 5, 0, 1200);

    static {
        BUILDER.pop();
        BUILDER.comment("Filter entries per side (whitelist or blacklist).").push("filter");
    }

    public static final ModConfigSpec.IntValue BASE_FILTER_SLOTS = BUILDER
            .comment("Entries available without Filter upgrades.")
            .defineInRange("baseFilterSlots", 9, 1, 54);

    public static final ModConfigSpec.IntValue FILTER_SLOTS_PER_UPGRADE = BUILDER
            .comment("Entries added by each Filter upgrade (Knozy counts as one).")
            .defineInRange("filterSlotsPerUpgrade", 9, 0, 54);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.IntValue MAX_NETWORK_SIZE = BUILDER
            .comment("Maximum number of pipes scanned when a side rebuilds its target list.")
            .defineInRange("maxNetworkSize", 512, 16, 8192);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private FlowlineConfig() {}
}
