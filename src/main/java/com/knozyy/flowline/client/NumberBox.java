package com.knozyy.flowline.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A small numeric text field. Typing sends the value right away; the mouse wheel steps it (Shift x10, Ctrl x100).
 * While it is not focused it follows the server's value.
 */
public class NumberBox extends EditBox {
    private final int min, max;
    private final IntSupplier server;
    private final IntConsumer send;
    private int shown;

    public NumberBox(Font font, int x, int y, int width, int min, int max, IntSupplier server, IntConsumer send) {
        super(font, x, y, width, 11, Component.empty());
        this.min = min;
        this.max = max;
        this.server = server;
        this.send = send;
        this.shown = server.getAsInt();
        setMaxLength(11);
        setValue(Integer.toString(shown));
        setFilter(text -> text.isEmpty() || text.equals("-") && min < 0 || text.matches(min < 0 ? "-?\\d{1,10}" : "\\d{1,10}"));
        setResponder(text -> {
            int value = parse(text);
            if (value != shown) {
                shown = value;
                this.send.accept(value);
            }
        });
    }

    private int parse(String text) {
        try {
            return (int) Math.max(min, Math.min(max, Long.parseLong(text)));
        } catch (NumberFormatException e) {
            return Math.max(min, Math.min(max, 0));
        }
    }

    /** Keeps showing the server's value unless the player is typing. */
    public void follow() {
        int value = server.getAsInt();
        if (!isFocused() && value != shown) {
            shown = value;
            setValue(Integer.toString(value));
        }
    }

    public void step(double scroll) {
        int step = Screen.hasControlDown() ? 100 : Screen.hasShiftDown() ? 10 : 1;
        long next = (long) shown + (scroll > 0 ? step : -step);
        int value = (int) Math.max(min, Math.min(max, next));
        if (value == shown) return;
        shown = value;
        setValue(Integer.toString(value));
        send.accept(value);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) setValue(Integer.toString(shown));
    }
}
