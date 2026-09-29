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

    public static final ModConfigSpec.IntValue CHEMICAL_PER_OPERATION = BUILDER
            .comment("Millibuckets of Mekanism chemicals (chemical pipe, only with Mekanism installed).")
            .defineInRange("chemicalPerOperation", 100, 1, 1_000_000);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on the amounts above, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "Entry 0 is used without upgrades; the last entry is used for any higher count.")
            .defineList("stackMultipliers", List.of(1, 8, 16, 32, 64, 96, 128),
                    o -> o instanceof Integer i && i >= 1);

    static {
        BUILDER.pop();
        BUILDER.comment("Adaptive operation interval (ticks). A side starts at startInterval, speeds up by",
                "accelerationStep after each operation that moved something (down to minInterval) and backs off",
                "exponentially (x idleBackoffFactor) after each one that moved nothing (up to maxIdleInterval).",
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

    public static final ModConfigSpec.DoubleValue IDLE_BACKOFF_FACTOR = BUILDER
            .comment("Multiplier on the interval after an operation that moved nothing (2 = double it).")
            .defineInRange("idleBackoffFactor", 2.0, 1.0, 16.0);

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
            .comment("Maximum number of pipes in one cached network graph.")
            .defineInRange("maxNetworkSize", 512, 16, 8192);

    public static final ModConfigSpec.BooleanValue SEND_ANIMATIONS = BUILDER
            .comment("Tell nearby players about moved items so they can draw them travelling through the pipes.",
                    "Each player can still turn the drawing off in the client config.")
            .define("sendItemAnimations", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Per-player settings (config/flowline-client.toml). */
    public static final class Client {
        private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

        public static final ModConfigSpec.BooleanValue RENDER_ITEMS = CLIENT
                .comment("Draw items travelling through pipes (needs sendItemAnimations on the server).")
                .define("renderTravellingItems", true);

        public static final ModConfigSpec.IntValue MAX_TRAVELLING = CLIENT
                .comment("Most travelling items drawn at once; older ones are dropped first.")
                .defineInRange("maxTravellingItems", 256, 0, 4096);

        public static final ModConfigSpec.IntValue TICKS_PER_PIPE = CLIENT
                .comment("Ticks a travelling item needs to pass one pipe.")
                .defineInRange("ticksPerPipe", 4, 1, 40);

        public static final ModConfigSpec SPEC = CLIENT.build();

        private Client() {}
    }

    private FlowlineConfig() {}
}
