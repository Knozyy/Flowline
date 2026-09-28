package com.knozyy.flowline.pipe;

import net.minecraft.util.StringRepresentable;

/** What a pipe side is attached to. Drives both the blockstate arm model and the collision shape. */
public enum Conn implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    ENDPOINT("endpoint");

    private final String name;

    Conn(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
