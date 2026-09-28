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

    private Button modeButton;
    private Button distributionButton;
    private Button redstoneButton;
    private Button whitelistButton;
    private Button matchButton;
    private Button clearButton;

    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 232;
        this.inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8;
        modeButton = addRenderableWidget(button(PipeMenu.BTN_MODE, x, topPos + 18, 160, 20));
        distributionButton = addRenderableWidget(button(PipeMenu.BTN_DISTRIBUTION, x, topPos + 40, 160, 20));
        redstoneButton = addRenderableWidget(button(PipeMenu.BTN_REDSTONE, x, topPos + 62, 160, 20));
        whitelistButton = addRenderableWidget(button(PipeMenu.BTN_WHITELIST, x, topPos + 84, 78, 20));
        matchButton = addRenderableWidget(button(PipeMenu.BTN_MATCH, x + 82, topPos + 84, 78, 20));
        clearButton = addRenderableWidget(button(PipeMenu.BTN_CLEAR, leftPos + imageWidth - 8 - 50, topPos + 104, 50, 12));
    }

    private Button button(int id, int x, int y, int width, int height) {
        return Button.builder(Component.empty(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
        }).bounds(x, y, width, height).build();
    }

    private void refreshButtons() {
        modeButton.setMessage(Component.translatable("gui.flowline.mode",
                Component.translatable("mode.flowline." + menu.mode().name().toLowerCase())));
        distributionButton.setMessage(Component.translatable("gui.flowline.distribution",
                Component.translatable("distribution.flowline." + menu.distribution().name().toLowerCase())));
        redstoneButton.setMessage(Component.translatable("gui.flowline.redstone",
                Component.translatable("redstone.flowline." + menu.redstone().name().toLowerCase())));
        whitelistButton.setMessage(Component.translatable(menu.whitelist()
                ? "gui.flowline.whitelist" : "gui.flowline.blacklist"));
        matchButton.setMessage(Component.translatable(menu.matchComponents()
                ? "gui.flowline.match_components" : "gui.flowline.ignore_components"));
        clearButton.setMessage(Component.translatable("gui.flowline.clear"));

        boolean hasFilter = menu.type != PipeType.ENERGY;
        whitelistButton.visible = hasFilter;
        matchButton.visible = hasFilter;
        clearButton.visible = hasFilter;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
            graphics.fill(x, y, x + 16, y + 16, SLOT_LIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        if (menu.type != PipeType.ENERGY) {
            graphics.drawString(font, Component.translatable("gui.flowline.filter"), 8, 108, TEXT, false);
        }
        Component speed = Component.translatable("gui.flowline.speed", menu.speed().multiplier);
        graphics.drawString(font, speed, imageWidth - 8 - font.width(speed), titleLabelY, TEXT, false);
    }
}
