package com.knozyy.flowline.pipe;

/** Order in which an extracting side offers its contents to the insert targets in the network. */
public enum Distribution {
    NEAREST,
    FARTHEST,
    ROUND_ROBIN,
    RANDOM,
    /** Splits every operation evenly between the targets that accept. */
    BALANCED,
    /** Highest insert priority first; ties go to the nearest target. */
    PRIORITY;

    public Distribution next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Distribution previous() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }

    public static Distribution byName(String name) {
        for (Distribution d : values()) {
            if (d.name().equals(name)) return d;
        }
        return NEAREST;
    }
}
