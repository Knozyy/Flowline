package com.knozyy.flowline.client;

import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Plain-drawn configuration screen (no texture file): buttons on top, ghost filter slots, player inventory. */
public class PipeScreen extends AbstractContainerScreen<PipeMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_BORDER = 0xFF2B2B2B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFF8B8B8B;
    private static final int TEXT = 0x404040;
    private static final int UPGRADE_BORDER = 0xFF7A4AB0;

    private Button distributionButton;
    private Button redstoneButton;
    private Button whitelistButton;
    private Button matchButton;
    private Button clearButton;

    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 212;
        this.inventoryLabelY = PipeMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8;
        distributionButton = addRenderableWidget(button(PipeMenu.BTN_DISTRIBUTION, x, topPos + 18, 136, 20));
        redstoneButton = addRenderableWidget(button(PipeMenu.BTN_REDSTONE, x, topPos + 40, 136, 20));
        whitelistButton = addRenderableWidget(button(PipeMenu.BTN_WHITELIST, x, topPos + 62, 78, 20));
        matchButton = addRenderableWidget(button(PipeMenu.BTN_MATCH, x + 82, topPos + 62, 78, 20));
        clearButton = addRenderableWidget(button(PipeMenu.BTN_CLEAR, leftPos + imageWidth - 8 - 50, topPos + 84, 50, 12));
    }

    private Button button(int id, int x, int y, int width, int height) {
        return Button.builder(Component.empty(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
        }).bounds(x, y, width, height).build();
    }

    private void refreshButtons() {
        distributionButton.setMessage(Component.translatable("gui.flowline.distribution",
                Component.translatable("distribution.flowline." + menu.distribution().name().toLowerCase())));
        redstoneButton.setMessage(Component.translatable("gui.flowline.redstone",
                Component.translatable("redstone.flowline." + menu.redstone().name().toLowerCase())));
        whitelistButton.setMessage(Component.translatable(menu.whitelist()
                ? "gui.flowline.whitelist" : "gui.flowline.blacklist"));
        matchButton.setMessage(Component.translatable(menu.matchComponents()
                ? "gui.flowline.match_components" : "gui.flowline.ignore_components"));
        clearButton.setMessage(Component.translatable("gui.flowline.clear"));

        boolean hasFilter = menu.hasFilter();
        whitelistButton.visible = hasFilter;
        matchButton.visible = hasFilter;
        clearButton.visible = hasFilter;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        Slot upgrade = menu.upgradeSlot();
        if (!upgrade.hasItem() && menu.getCarried().isEmpty() && isHovering(upgrade.x, upgrade.y, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.flowline.upgrade_slot"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (slot == menu.upgradeSlot()) {
                graphics.fill(x - 2, y - 2, x + 18, y + 18, UPGRADE_BORDER);
            }
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
            graphics.fill(x, y, x + 16, y + 16, SLOT_LIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        if (menu.hasFilter()) {
            graphics.drawString(font, Component.translatable("gui.flowline.filter"), 8, 86, TEXT, false);
        } else if (menu.type != PipeType.ENERGY) {
            graphics.drawWordWrap(font, Component.translatable("gui.flowline.filter_needs_upgrade"), 8, 68,
                    imageWidth - 16, TEXT);
        }
        Component status = Component.translatable("gui.flowline.status",
                Component.translatable("mode.flowline." + menu.mode().name().toLowerCase()), menu.speed().multiplier);
        graphics.drawString(font, status, imageWidth - 8 - font.width(status), titleLabelY, TEXT, false);
    }
}
