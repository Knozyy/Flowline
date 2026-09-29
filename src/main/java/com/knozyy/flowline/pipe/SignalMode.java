package com.knozyy.flowline.pipe;

/** Redstone output of an extracting side: when the pipe gives a signal to its neighbours. */
public enum SignalMode {
    OFF,
    /** While the side's last operation moved something. */
    MOVING,
    /** While the source has something the side may take but its last operation could not deliver any of it. */
    STUCK;

    public SignalMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SignalMode previous() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }

    public static SignalMode byName(String name) {
        for (SignalMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return OFF;
    }
}
