package com.knozyy.flowline.pipe;

/** How a pipe treats the block attached to one of its sides. Toggled with sneak + right-click. */
public enum SideMode {
    INSERT,
    EXTRACT;

    public SideMode toggle() {
        return this == INSERT ? EXTRACT : INSERT;
    }

    public static SideMode byName(String name) {
        return EXTRACT.name().equals(name) ? EXTRACT : INSERT;
    }
}
