package com.knozyy.flowline.pipe;

/** How a pipe treats the inventory/tank/machine attached to one of its sides. */
public enum SideMode {
    INSERT,
    EXTRACT,
    DISABLED;

    public SideMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SideMode byName(String name) {
        for (SideMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return INSERT;
    }
}
