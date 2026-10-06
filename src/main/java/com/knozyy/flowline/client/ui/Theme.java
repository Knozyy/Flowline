package com.knozyy.flowline.client.ui;

import com.knozyy.flowline.pipe.PipeType;

/**
 * "Cast iron" palette of the pipe screens. The accent comes from the pipe type and is only used as a fill or a
 * border; text on it is {@link #ON_ACCENT}. Contrast (WCAG): text on bg 10.8, on panel 9.2, muted on panel 5.1.
 */
public final class Theme {
    public static final int BG = 0xFF34302D;
    public static final int PANEL = 0xFF403B37;
    public static final int LINE = 0xFF5A534D;
    public static final int EDGE = 0xFF6B635B;
    public static final int SLOT = 0xFF2A2724;
    public static final int FRAME = 0xFF1E1B19;
    public static final int HOVER = 0xFF4A443F;
    public static final int TEXT = 0xFFEFE9E2;
    public static final int MUTED = 0xFFB9AFA5;
    public static final int ON_ACCENT = 0xFF1B1F23;

    private Theme() {}

    /** The pipe's own colour, as its rails and textures show it (TYPES in tools/gen_resources.py). */
    public static int accent(PipeType type) {
        return switch (type) {
            case ITEM -> 0xFFF4D58D;
            case FLUID -> 0xFF7FB7E6;
            case ENERGY -> 0xFFF2A07B;
            case UNIVERSAL -> 0xFFB79CE8;
            case CHEMICAL -> 0xFF8FD1A4;
        };
    }
}
