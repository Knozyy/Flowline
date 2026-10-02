package com.knozyy.flowline.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing helpers shared by the pipe screens. Coordinates are absolute; right and bottom are exclusive. */
public final class Ui {
    private Ui() {}

    /** Window body with clipped corners. */
    public static void window(GuiGraphics g, int l, int t, int r, int b) {
        g.fill(l + 1, t - 1, r - 1, b + 1, Theme.FRAME);
        g.fill(l - 1, t + 1, r + 1, b - 1, Theme.FRAME);
        g.fill(l, t, r, b, Theme.BG);
    }

    public static void panel(GuiGraphics g, int l, int t, int r, int b) {
        g.fill(l, t, r, b, Theme.LINE);
        g.fill(l + 1, t + 1, r - 1, b - 1, Theme.PANEL);
    }

    public static void frame(GuiGraphics g, int l, int t, int r, int b, int color) {
        g.fill(l, t, r, t + 1, color);
        g.fill(l, b - 1, r, b, color);
        g.fill(l, t + 1, l + 1, b - 1, color);
        g.fill(r - 1, t + 1, r, b - 1, color);
    }

    /** Border made of {@code dash}-long pieces with gaps of the same length. */
    public static void dashed(GuiGraphics g, int l, int t, int r, int b, int color, int dash) {
        for (int x = l; x < r; x += dash * 2) {
            g.fill(x, t, Math.min(x + dash, r), t + 1, color);
            g.fill(x, b - 1, Math.min(x + dash, r), b, color);
        }
        for (int y = t; y < b; y += dash * 2) {
            g.fill(l, y, l + 1, Math.min(y + dash, b), color);
            g.fill(r - 1, y, r, Math.min(y + dash, b), color);
        }
    }

    /** A slot or text field background: dark well with a line around it. */
    public static void well(GuiGraphics g, int l, int t, int r, int b) {
        g.fill(l, t, r, b, Theme.LINE);
        g.fill(l + 1, t + 1, r - 1, b - 1, Theme.SLOT);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Text ending at {@code right}. */
    public static void textRight(GuiGraphics g, Font font, Component text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    public static void textCentered(GuiGraphics g, Font font, Component text, int center, int y, int color) {
        g.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    /** {@code text} cut to {@code width} pixels with an ellipsis. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    public static boolean fits(Font font, String text, int width) {
        return font.width(text) <= width;
    }

    /**
     * A label chip: filled (light fill, dark text) or outlined. Returns its width.
     */
    public static int chip(GuiGraphics g, Font font, Component text, int x, int y, boolean filled) {
        int w = font.width(text) + 6;
        if (filled) {
            g.fill(x, y, x + w, y + 11, Theme.TEXT);
            g.drawString(font, text, x + 3, y + 2, Theme.BG, false);
        } else {
            frame(g, x, y, x + w, y + 11, Theme.EDGE);
            g.drawString(font, text, x + 3, y + 2, Theme.TEXT, false);
        }
        return w;
    }

    public static int chipWidth(Font font, Component text) {
        return font.width(text) + 6;
    }

    public static void scrollbar(GuiGraphics g, int x, int t, int b, int scroll, int rows, int visible, int accent) {
        if (rows <= visible) return;
        int track = b - t;
        int thumb = Math.max(6, track * visible / rows);
        int y = t + (track - thumb) * scroll / Math.max(1, rows - visible);
        g.fill(x, t, x + 2, b, Theme.LINE);
        g.fill(x, y, x + 2, y + thumb, accent);
    }

    /** Checkbox drawn at 9x9. */
    public static void checkbox(GuiGraphics g, int x, int y, boolean checked, int accent) {
        if (checked) {
            g.fill(x, y, x + 9, y + 9, accent);
            g.fill(x + 2, y + 4, x + 4, y + 6, Theme.ON_ACCENT);
            g.fill(x + 4, y + 5, x + 5, y + 7, Theme.ON_ACCENT);
            g.fill(x + 5, y + 2, x + 7, y + 5, Theme.ON_ACCENT);
        } else {
            frame(g, x, y, x + 9, y + 9, Theme.MUTED);
        }
    }

    public static boolean in(double mx, double my, int l, int t, int r, int b) {
        return mx >= l && mx < r && my >= t && my < b;
    }
}
