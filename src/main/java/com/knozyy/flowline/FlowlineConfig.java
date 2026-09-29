package com.knozyy.flowline;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Common config. Values are read at runtime, never cached, so edits apply without a restart. */
public final class FlowlineConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("Amount moved per operation by an extracting side, before Stack upgrades.").push("amounts");
    }

    public static final ForgeConfigSpec.IntValue ITEMS_PER_OPERATION = BUILDER
            .comment("Items per operation without Stack upgrades.")
            .defineInRange("itemsPerOperation", 16, 1, 4096);

    public static final ForgeConfigSpec.IntValue FLUID_PER_OPERATION = BUILDER
            .comment("Millibuckets per operation without Stack upgrades (1000 = one bucket).")
            .defineInRange("fluidPerOperation", 1000, 1, 1_000_000);

    public static final ForgeConfigSpec.IntValue ENERGY_PER_OPERATION = BUILDER
            .comment("FE.")
            .defineInRange("energyPerOperation", 1000, 1, 100_000_000);

    public static final ForgeConfigSpec.IntValue CHEMICAL_PER_OPERATION = BUILDER
            .comment("Millibuckets of Mekanism chemicals per operation without Stack upgrades (chemical pipe, only",
                    "with Mekanism installed).")
            .defineInRange("chemicalPerOperation", 1000, 1, 1_000_000);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on energyPerOperation, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "Entry 0 is used without upgrades; the last entry is used for any higher count.")
            .defineList("stackMultipliers", List.of(1, 8, 16, 32, 64, 96, 128),
                    o -> o instanceof Integer i && i >= 1);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> ITEM_STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on itemsPerOperation, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "The default doubles per upgrade: 16, 32, 64, 128, 256, 512, 1024 items per operation.")
            .defineList("itemStackMultipliers", List.of(1, 2, 4, 8, 16, 32, 64),
                    o -> o instanceof Integer i && i >= 1);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FLUID_STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on fluidPerOperation, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "The default doubles per upgrade: 1, 2, 4 ... 64 buckets per operation.")
            .defineList("fluidStackMultipliers", List.of(1, 2, 4, 8, 16, 32, 64),
                    o -> o instanceof Integer i && i >= 1);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> CHEMICAL_STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on chemicalPerOperation, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "The default doubles per upgrade: 1, 2, 4 ... 64 buckets per operation.")
            .defineList("chemicalStackMultipliers", List.of(1, 2, 4, 8, 16, 32, 64),
                    o -> o instanceof Integer i && i >= 1);

    static {
        BUILDER.pop();
        BUILDER.comment("Adaptive operation interval (ticks). A side starts at startInterval, speeds up by",
                "accelerationStep after each operation that moved something (down to minInterval) and backs off",
                "exponentially (x idleBackoffFactor) after each one that moved nothing (up to maxIdleInterval).",
                "Sides without any target sleep until the pipe network changes.").push("pacing");
    }

    public static final ForgeConfigSpec.IntValue START_INTERVAL = BUILDER
            .defineInRange("startInterval", 30, 1, 1200);

    public static final ForgeConfigSpec.IntValue MIN_INTERVAL = BUILDER
            .defineInRange("minInterval", 5, 1, 1200);

    public static final ForgeConfigSpec.IntValue MAX_IDLE_INTERVAL = BUILDER
            .defineInRange("maxIdleInterval", 100, 1, 12000);

    public static final ForgeConfigSpec.IntValue SPEED_REDUCTION = BUILDER
            .comment("Ticks each Speed upgrade (or Knozy) removes from startInterval.")
            .defineInRange("speedReduction", 4, 0, 1200);

    public static final ForgeConfigSpec.IntValue ACCELERATION_STEP = BUILDER
            .defineInRange("accelerationStep", 2, 0, 1200);

    public static final ForgeConfigSpec.DoubleValue IDLE_BACKOFF_FACTOR = BUILDER
            .comment("Multiplier on the interval after an operation that moved nothing (2 = double it).")
            .defineInRange("idleBackoffFactor", 2.0, 1.0, 16.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Filter entries per side (whitelist or blacklist).").push("filter");
    }

    public static final ForgeConfigSpec.IntValue BASE_FILTER_SLOTS = BUILDER
            .comment("Entries available without Filter upgrades.")
            .defineInRange("baseFilterSlots", 9, 1, 54);

    public static final ForgeConfigSpec.IntValue FILTER_SLOTS_PER_UPGRADE = BUILDER
            .comment("Entries added by each Filter upgrade (Knozy counts as one).")
            .defineInRange("filterSlotsPerUpgrade", 9, 0, 54);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec.IntValue MAX_NETWORK_SIZE = BUILDER
            .comment("Maximum number of pipes in one cached network graph.")
            .defineInRange("maxNetworkSize", 512, 16, 8192);


    public static final ForgeConfigSpec SPEC = BUILDER.build();


    private FlowlineConfig() {}
}
