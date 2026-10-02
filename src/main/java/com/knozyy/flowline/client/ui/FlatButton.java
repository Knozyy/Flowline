package com.knozyy.flowline.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Text button in the pipe screens' style. {@link Style#PRIMARY} is filled with the accent, {@link Style#SELECTED}
 * with the text colour (one half of a two-way switch), {@link Style#PLAIN} only outlined. A right-click runs
 * {@code back} when one is given, so cycling buttons can go both ways. Tooltips are drawn by the screen.
 */
public class FlatButton extends Button {
    public enum Style { PLAIN, PRIMARY, SELECTED }

    private final Supplier<Component> label;
    private final int accent;
    private Supplier<Style> style = () -> Style.PLAIN;
    private BooleanSupplier warning = () -> false;
    private Runnable back;
    private boolean centered = true;

    public FlatButton(int x, int y, int width, int height, Supplier<Component> label, int accent, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.label = label;
        this.accent = accent;
    }

    public FlatButton style(Style style) {
        this.style = () -> style;
        return this;
    }

    public FlatButton style(Supplier<Style> style) {
        this.style = style;
        return this;
    }

    /** Red border while true, e.g. a destructive button waiting for its confirming click. */
    public FlatButton warnWhen(BooleanSupplier warning) {
        this.warning = warning;
        return this;
    }

    public FlatButton onBack(Runnable back) {
        this.back = back;
        return this;
    }

    public FlatButton leftAligned() {
        this.centered = false;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && back != null && active && visible && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            back.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        Component text = label.get();
        setMessage(text);
        int l = getX(), t = getY(), r = l + width, b = t + height;
        boolean hot = active && isHoveredOrFocused();
        Style current = style.get();
        int textColor;
        switch (current) {
            case PRIMARY -> {
                g.fill(l, t, r, b, active ? accent : Theme.LINE);
                textColor = active ? Theme.ON_ACCENT : Theme.MUTED;
            }
            case SELECTED -> {
                g.fill(l, t, r, b, Theme.TEXT);
                textColor = Theme.BG;
            }
            default -> {
                if (hot) g.fill(l, t, r, b, Theme.HOVER);
                Ui.frame(g, l, t, r, b, hot ? accent : Theme.EDGE);
                textColor = active ? Theme.TEXT : Theme.MUTED;
            }
        }
        if (warning.getAsBoolean()) Ui.frame(g, l, t, r, b, 0xFFFF5555);
        String shown = Ui.fit(font, text.getString(), width - 4);
        int x = centered ? l + (width - font.width(shown)) / 2 : l + 3;
        g.drawString(font, shown, x, t + (height - 8) / 2, textColor, false);
    }
}
