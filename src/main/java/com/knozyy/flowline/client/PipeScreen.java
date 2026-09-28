package com.knozyy.flowline.client;

import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SpeedTier;
import com.knozyy.flowline.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dark, panel-based configuration screen drawn without a background texture: a header with the pipe and side,
 * then three panels (settings icons, 3x3 filter, upgrade), then the player inventory.
 */
public class PipeScreen extends AbstractContainerScreen<PipeMenu> {
    private static final int BG = 0xFF1E2229;
    private static final int BG_EDGE = 0xFF0E1014;
    private static final int PANEL = 0xFF262B33;
    private static final int PANEL_EDGE = 0xFF343A45;
    private static final int SLOT = 0xFF14171C;
    private static final int SLOT_EDGE = 0xFF3A404C;
    private static final int TEXT = 0xFFE6E9EF;
    private static final int MUTED = 0xFF8A93A3;

    // Panel bounds, relative to the screen origin.
    private static final int PANEL_TOP = 30;
    private static final int PANEL_BOTTOM = 98;
    private static final int SETTINGS_L = 6, SETTINGS_R = 52;
    private static final int FILTER_L = 56, FILTER_R = 118;
    private static final int UPGRADE_L = 122, UPGRADE_R = 170;

    private final int accent;

    private IconButton redstoneButton;
    private IconButton distributionButton;
    private IconButton whitelistButton;
    private IconButton matchButton;
    private IconButton clearButton;

    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 196;
        this.inventoryLabelY = PipeMenu.INVENTORY_Y - 10;
        this.accent = switch (menu.type) {
            case ITEM -> 0xFFE08A2B;
            case FLUID -> 0xFF2F7FE0;
            case ENERGY -> 0xFFD83A3A;
        };
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + SETTINGS_L + 2;
        int y = topPos + PANEL_TOP + 12;
        redstoneButton = addRenderableWidget(new IconButton(x, y, 20,
                () -> "redstone_" + key(menu.redstone()), accent, b -> press(PipeMenu.BTN_REDSTONE)));
        distributionButton = addRenderableWidget(new IconButton(x + 22, y, 20,
                () -> "distribution_" + key(menu.distribution()), accent, b -> press(PipeMenu.BTN_DISTRIBUTION)));
        whitelistButton = addRenderableWidget(new IconButton(x, y + 22, 20,
                () -> menu.whitelist() ? "whitelist" : "blacklist", accent, b -> press(PipeMenu.BTN_WHITELIST)));
        matchButton = addRenderableWidget(new IconButton(x + 22, y + 22, 20,
                () -> menu.matchComponents() ? "match_components" : "ignore_components", accent,
                b -> press(PipeMenu.BTN_MATCH)));
        clearButton = addRenderableWidget(new IconButton(leftPos + FILTER_R - 13, topPos + PANEL_TOP + 1, 11,
                () -> "clear", accent, b -> press(PipeMenu.BTN_CLEAR)));
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean filter = menu.hasFilter();
        whitelistButton.active = filter;
        matchButton.active = filter;
        clearButton.active = filter;
        clearButton.visible = menu.type != PipeType.ENERGY;

        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderButtonTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos, r = leftPos + imageWidth, b = topPos + imageHeight;

        // window with clipped corners and an accent strip on top
        graphics.fill(l + 1, t - 1, r - 1, b + 1, BG_EDGE);
        graphics.fill(l - 1, t + 1, r + 1, b - 1, BG_EDGE);
        graphics.fill(l, t, r, b, BG);
        graphics.fill(l + 1, t, r - 1, t + 2, accent);

        panel(graphics, SETTINGS_L, SETTINGS_R);
        panel(graphics, FILTER_L, FILTER_R);
        panel(graphics, UPGRADE_L, UPGRADE_R);

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = l + slot.x;
            int y = t + slot.y;
            boolean upgrade = slot == menu.upgradeSlot();
            graphics.fill(x - 1, y - 1, x + 17, y + 17, upgrade ? accent : SLOT_EDGE);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
        }

        // header icon
        graphics.renderItem(pipeStack(), l + 6, t + 6);

        // locked / missing filter
        if (!menu.hasFilter()) {
            int cx = l + (FILTER_L + FILTER_R) / 2;
            if (menu.type == PipeType.ENERGY) {
                smallCentered(graphics, Component.translatable("gui.flowline.no_filter"), cx, t + 66, MUTED);
            } else {
                RenderSystem.enableBlend();
                graphics.blit(IconButton.icon("lock"), cx - 8, t + 54, 0, 0, 16, 16, 16, 16);
                RenderSystem.disableBlend();
                smallCentered(graphics, Component.translatable("gui.flowline.needs_upgrade"), cx, t + 74, MUTED);
            }
        }
    }

    private void panel(GuiGraphics graphics, int left, int right) {
        int l = leftPos + left, r = leftPos + right, t = topPos + PANEL_TOP, b = topPos + PANEL_BOTTOM;
        graphics.fill(l, t, r, b, PANEL_EDGE);
        graphics.fill(l + 1, t + 1, r - 1, b - 1, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // header
        Component name = Component.translatable("block.flowline." + menu.type.getSerializedName() + "_pipe");
        graphics.drawString(font, name, 26, 7, TEXT, false);
        Component sub = Component.translatable("gui.flowline.side_line",
                Component.translatable("direction.flowline." + menu.side.getName()),
                Component.translatable("mode.flowline." + key(menu.mode())));
        small(graphics, sub, 26, 18, MUTED);

        // speed badge
        String speed = "x" + menu.speed().multiplier;
        int w = font.width(speed) + 8;
        int bx = imageWidth - 7 - w;
        graphics.fill(bx, 8, bx + w, 20, menu.speed().isUpgraded() ? accent : PANEL_EDGE);
        graphics.drawString(font, speed, bx + 4, 10, TEXT, false);

        // panel captions
        small(graphics, caption("gui.flowline.section.settings"), SETTINGS_L + 4, PANEL_TOP + 4, MUTED);
        small(graphics, caption("gui.flowline.section.filter"), FILTER_L + 4, PANEL_TOP + 4, MUTED);
        int ucx = (UPGRADE_L + UPGRADE_R) / 2;
        smallCentered(graphics, caption("gui.flowline.section.upgrade"), ucx, PANEL_TOP + 4, MUTED);

        // upgrade panel: tier and interval
        SpeedTier tier = menu.speed();
        Component tierName = tier.isUpgraded()
                ? Component.translatable("tier.flowline." + key(tier))
                : Component.translatable("tier.flowline.none");
        smallCentered(graphics, tierName, ucx, PipeMenu.UPGRADE_Y + 22, tier.isUpgraded() ? TEXT : MUTED);
        smallCentered(graphics, Component.translatable("gui.flowline.interval", tier.interval), ucx,
                PipeMenu.UPGRADE_Y + 32, MUTED);

        small(graphics, playerInventoryTitle, 8, inventoryLabelY, MUTED);
    }

    private static Component caption(String key) {
        return Component.literal(Component.translatable(key).getString().toUpperCase(Locale.ROOT));
    }

    private ItemStack pipeStack() {
        return switch (menu.type) {
            case ITEM -> new ItemStack(ModItems.ITEM_PIPE.get());
            case FLUID -> new ItemStack(ModItems.FLUID_PIPE.get());
            case ENERGY -> new ItemStack(ModItems.ENERGY_PIPE.get());
        };
    }

    // ---- tooltips -----------------------------------------------------------------------------------------

    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = null;
        if (redstoneButton.isHovered()) {
            lines = describe("gui.flowline.redstone", "redstone.flowline." + key(menu.redstone()), true);
        } else if (distributionButton.isHovered()) {
            lines = describe("gui.flowline.distribution", "distribution.flowline." + key(menu.distribution()), true);
        } else if (whitelistButton.isHovered()) {
            String k = menu.whitelist() ? "whitelist" : "blacklist";
            lines = describe("gui.flowline.filter_mode", "gui.flowline." + k, whitelistButton.active);
        } else if (matchButton.isHovered()) {
            String k = menu.matchComponents() ? "match_components" : "ignore_components";
            lines = describe("gui.flowline.nbt", "gui.flowline." + k, matchButton.active);
        } else if (clearButton.visible && clearButton.isHovered()) {
            lines = new ArrayList<>(List.of(Component.translatable("gui.flowline.clear")));
            if (!clearButton.active) lines.add(needsUpgrade());
        } else if (!menu.upgradeSlot().hasItem() && menu.getCarried().isEmpty()
                && isHovering(menu.upgradeSlot().x, menu.upgradeSlot().y, 16, 16, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.flowline.upgrade_slot"),
                    Component.translatable("gui.flowline.upgrade_slot.desc").withStyle(ChatFormatting.GRAY));
        }
        if (lines != null) graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** "Label: Value", a grey description line and, when locked, why. */
    private List<Component> describe(String labelKey, String valueKey, boolean enabled) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(labelKey, Component.translatable(valueKey).withColor(accent)));
        lines.add(Component.translatable(valueKey + ".desc").withStyle(ChatFormatting.GRAY));
        if (!enabled) lines.add(needsUpgrade());
        return lines;
    }

    private static Component needsUpgrade() {
        return Component.translatable("gui.flowline.needs_upgrade").withStyle(ChatFormatting.RED);
    }

    // ---- small text ---------------------------------------------------------------------------------------

    private void small(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void smallCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        List<FormattedCharSequence> lines = font.split(text, 76);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float w = font.width(line) * 0.75f;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX - w / 2f, y + i * 8, 0);
            graphics.pose().scale(0.75f, 0.75f, 1f);
            graphics.drawString(font, line, 0, 0, color, false);
            graphics.pose().popPose();
        }
    }
}
