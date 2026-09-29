package com.knozyy.flowline.pipe;

/** When an extracting side is allowed to run, based on the redstone signal at the pipe. */
public enum RedstoneMode {
    IGNORED,
    REQUIRE_SIGNAL,
    REQUIRE_NO_SIGNAL,
    /** One operation per rising edge of the signal; the adaptive interval is not used. */
    PULSE;

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public RedstoneMode previous() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }

    /** Whether a continuously running side may work; pulse sides are driven by edges instead. */
    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case REQUIRE_SIGNAL -> powered;
            case REQUIRE_NO_SIGNAL -> !powered;
            case PULSE -> false;
        };
    }

    public static RedstoneMode byName(String name) {
        for (RedstoneMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return IGNORED;
    }
}
