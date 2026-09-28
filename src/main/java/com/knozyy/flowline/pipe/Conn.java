package com.knozyy.flowline.pipe;

import net.minecraft.util.StringRepresentable;

/** What a pipe side is attached to. Drives both the blockstate arm model and the collision shape. */
public enum Conn implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    /** Attached to a block, inserting into it. */
    ENDPOINT("endpoint"),
    /** Attached to a block and extracting from it; drawn with a green ring so it can be told apart. */
    EXTRACT("extract");

    private final String name;

    Conn(String name) {
        this.name = name;
    }

    /** Attached to a block (inserting or extracting). */
    public boolean isEndpoint() {
        return this == ENDPOINT || this == EXTRACT;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
