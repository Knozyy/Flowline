package com.knozyy.flowline;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server config ({@code serverconfig/flowline-server.toml} in each world), synced to clients so tooltips show the
 * server's numbers. Values are read at runtime, never cached, so edits apply without a restart. Server configs are
 * only loaded while a world is open: code that can run outside one (tooltips) must check {@link #isLoaded()} first.
 * The in-game editor is {@code client.FlowlineConfigScreen}; labels are {@code flowline.configuration.<key>}.
 */
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

    public static final ForgeConfigSpec.IntValue ENERGY_PER_TICK = BUILDER
            .comment("FE per tick an extracting side moves without Stack upgrades. Energy pipes work every tick while",
                    "they have something to move, like a cable; universal pipes move the ticks since their last",
                    "operation at once. The default is a bit above a basic Mekanism cable (3200 FE/t).")
            .defineInRange("energyPerTick", 8000, 1, 100_000_000);

    public static final ForgeConfigSpec.IntValue CHEMICAL_PER_OPERATION = BUILDER
            .comment("Millibuckets of Mekanism chemicals per operation without Stack upgrades (chemical pipe, only",
                    "with Mekanism installed).")
            .defineInRange("chemicalPerOperation", 1000, 1, 1_000_000);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> STACK_MULTIPLIERS = BUILDER
            .comment("Multiplier on energyPerTick, indexed by the number of Stack upgrades (Knozy counts as one).",
                    "Entry 0 is used without upgrades; the last entry is used for any higher count. The default",
                    "gives 8000, 32000, 128000, 512000, 1024000, 2048000 and 8192000 FE/t.")
            .defineList("stackMultipliers", List.of(1, 4, 16, 64, 128, 256, 1024),
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
        BUILDER.comment("Pipe network scanning.").push("network");
    }

    public static final ForgeConfigSpec.IntValue MAX_NETWORK_SIZE = BUILDER
            .comment("Maximum number of pipes in one cached network graph.")
            .defineInRange("maxNetworkSize", 512, 16, 8192);

    public static final ForgeConfigSpec.BooleanValue ALLOW_NETWORK_VIEW = BUILDER
            .comment("Allow wrench users to see their pipe network through walls. Disable to hide endpoints.")
            .define("allowNetworkView", true);

    public static final ForgeConfigSpec.IntValue NETWORK_VIEW_RANGE = BUILDER
            .comment("Maximum distance from the player to pipes and endpoints in the network view.")
            .defineInRange("networkViewRange", 32, 4, 128);

    static {
        BUILDER.pop();
        BUILDER.comment("\"Build for me\": with a pipe in the off hand, a key (B by default) lays pipes from the block",
                "the player looks at back to the player.").push("building");
    }

    public static final ForgeConfigSpec.IntValue BUILD_RANGE = BUILDER
            .comment("How far away the looked-at block may be. Paths may be up to three times as long.")
            .defineInRange("buildRange", 32, 4, 128);

    static {
        BUILDER.pop();
        BUILDER.comment("Items drawn travelling through see-through pipes.").push("animations");
    }

    public static final ForgeConfigSpec.BooleanValue SEND_ANIMATIONS = BUILDER
            .comment("Tell nearby players about moved items so they can draw them travelling through the pipes.",
                    "Each player can still turn the drawing off in the client config.")
            .define("sendItemAnimations", true);

    public static final ForgeConfigSpec.BooleanValue SEND_FLUID_ANIMATIONS = BUILDER
            .comment("Tell nearby players about fluid moving through fluid pipes. Purely visual.")
            .define("sendFluidAnimations", true);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** False outside a world (main menu, before the server's values arrive), where values cannot be read. */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    /** Per-player settings (config/flowline-client.toml), editable at any time from the config screen. */
    public static final class Client {
        private static final ForgeConfigSpec.Builder CLIENT = new ForgeConfigSpec.Builder();

        public static final ForgeConfigSpec.BooleanValue RENDER_ITEMS = CLIENT
                .comment("Draw items travelling through pipes (needs sendItemAnimations on the server).")
                .define("renderTravellingItems", true);

        public static final ForgeConfigSpec.IntValue MAX_TRAVELLING = CLIENT
                .comment("Most travelling items drawn at once; older ones are dropped first.")
                .defineInRange("maxTravellingItems", 256, 0, 4096);

        public static final ForgeConfigSpec.IntValue TICKS_PER_PIPE = CLIENT
                .comment("Ticks a travelling item needs to pass one pipe.")
                .defineInRange("ticksPerPipe", 4, 1, 40);

        public static final ForgeConfigSpec.BooleanValue RENDER_NETWORK_VIEW = CLIENT
                .comment("Show the pipe network while sneaking with a wrench and looking at a pipe.")
                .define("renderNetworkView", true);

        public static final ForgeConfigSpec.BooleanValue RENDER_FLUIDS = CLIENT
                .comment("Draw moving fluid inside fluid pipes (needs sendFluidAnimations on the server).")
                .define("renderFluidInPipes", true);

        public static final ForgeConfigSpec.IntValue MAX_FLUID_PIPES = CLIENT
                .comment("Maximum number of pipes with fluid animations at once; older flows are dropped first.")
                .defineInRange("maxFluidPipes", 128, 0, 512);

        public static final ForgeConfigSpec.IntValue FLUID_RENDER_RANGE = CLIENT
                .comment("Maximum distance in blocks at which fluid inside pipes is drawn.")
                .defineInRange("fluidRenderRange", 32, 4, 64);

        public static final ForgeConfigSpec SPEC = CLIENT.build();

        private Client() {}
    }

    private FlowlineConfig() {}
}
