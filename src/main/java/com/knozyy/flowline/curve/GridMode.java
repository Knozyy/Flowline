package com.knozyy.flowline.curve;

import java.util.Locale;

/** Curvy Pipes' grid modes: where in a block cell a free point may sit (curvy-protocol-editor-re.md, "Grid"). */
public enum GridMode {
    OFF, AXIAL, CORNER_P, CORNER_N, EDGE_P, EDGE_N;

    public GridMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public GridMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public String key() {
        return "hud.flowline.grid." + name().toLowerCase(Locale.ROOT);
    }
}
