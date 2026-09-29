package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Flat square button that shows a 16x16 icon from {@code textures/gui/icon/}. Tooltips are drawn by the screen. */
public class IconButton extends Button {
    private static final int BORDER = 0xFF454C59;
    private static final int FILL = 0xFF2A2F38;
    private static final int FILL_HOVER = 0xFF3A414D;
    private static final int FILL_DISABLED = 0xFF1B1E24;
    private static final int WARNING = 0xFFFF5555;

    private final Supplier<String> icon;
    private final int accent;
    /** While true the icon is drawn faded (a toggle that is off), but the button stays clickable. */
    private final BooleanSupplier dim;
    /** While true the border is drawn red, e.g. a destructive button waiting for its confirming click. */
    private BooleanSupplier warning = () -> false;

    public IconButton(int x, int y, int size, Supplier<String> icon, int accent, OnPress onPress) {
        this(x, y, size, icon, accent, () -> false, onPress);
    }

    public IconButton(int x, int y, int size, Supplier<String> icon, int accent, BooleanSupplier dim, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.accent = accent;
        this.dim = dim;
    }

    public IconButton warnWhen(BooleanSupplier warning) {
        this.warning = warning;
        return this;
    }

    public static ResourceLocation icon(String name) {
        return new ResourceLocation(Flowline.MODID, "textures/gui/icon/" + name + ".png");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, warning.getAsBoolean() ? WARNING : hot ? accent : BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, !active ? FILL_DISABLED : hot ? FILL_HOVER : FILL);

        int size = Math.min(16, Math.min(width, height) - 4);
        RenderSystem.enableBlend();
        if (!active || dim.getAsBoolean()) graphics.setColor(1f, 1f, 1f, 0.3f);
        graphics.blit(icon(icon.get()), x + (width - size) / 2, y + (height - size) / 2, size, size,
                0, 0, 16, 16, 16, 16);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
