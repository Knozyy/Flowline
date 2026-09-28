package com.knozyy.flowline;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config (serverconfig-style values that are read at runtime, never cached). */
public final class FlowlineConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ITEMS_PER_OPERATION = BUILDER
            .comment("Items moved per operation by an extracting side without upgrades.")
            .defineInRange("itemsPerOperation", 4, 1, 4096);

    public static final ModConfigSpec.IntValue FLUID_PER_OPERATION = BUILDER
            .comment("Millibuckets moved per operation by an extracting side without upgrades.")
            .defineInRange("fluidPerOperation", 200, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ENERGY_PER_OPERATION = BUILDER
            .comment("Energy (FE) moved per operation by an extracting side without upgrades.")
            .defineInRange("energyPerOperation", 1000, 1, 100_000_000);

    public static final ModConfigSpec.IntValue MAX_NETWORK_SIZE = BUILDER
            .comment("Maximum number of pipes scanned per transfer. Larger values cost more server time.")
            .defineInRange("maxNetworkSize", 512, 16, 8192);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private FlowlineConfig() {}
}
