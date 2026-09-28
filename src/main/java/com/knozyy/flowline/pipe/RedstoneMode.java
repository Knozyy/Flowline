package com.knozyy.flowline.pipe;

/** When an extracting side is allowed to run, based on the redstone signal at the pipe. */
public enum RedstoneMode {
    IGNORED,
    REQUIRE_SIGNAL,
    REQUIRE_NO_SIGNAL;

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case REQUIRE_SIGNAL -> powered;
            case REQUIRE_NO_SIGNAL -> !powered;
        };
    }

    public static RedstoneMode byName(String name) {
        for (RedstoneMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return IGNORED;
    }
}
