package com.knozyy.flowline.client;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.network.SetSideValuePayload;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.knozyy.flowline.network.ModNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dark, panel-based configuration screen drawn without a background texture: a header with the pipe and side,
 * three panels (settings icons, 3x3 filter, upgrade), a row of numbers (priority, regulator, rate) and channel
 * toggles, then the player inventory. Extracting and inserting sides show different settings.
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
    private static final int VALUES_TOP = 100, VALUES_BOTTOM = 124;
    private static final int FIELD_W = 44;

    private final int accent;
    /** Pacing badge in the header, relative to the screen origin; recomputed every frame. */
    private int badgeX, badgeW;
    private static final int BADGE_Y = 8, BADGE_H = 12;

    private IconButton redstoneButton;
    private IconButton distributionButton;
    private IconButton clearButton;
    private IconButton overflowButton;
    private IconButton signalButton;
    /** Until when (ms) the clear button is armed: the first click only arms it, so rules are not lost to a misclick. */
    private long clearArmedUntil = 0;
    private IconButton prevPageButton;
    private IconButton nextPageButton;
    private IconButton upgradeHelp;
    private final List<IconButton> channelButtons = new ArrayList<>();
    /** Number fields with their label key and help key. */
    private final List<Field> fields = new ArrayList<>();

    private record Field(NumberBox box, String label, String help, int x) {}

    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 216;
        this.inventoryLabelY = PipeMenu.INVENTORY_Y - 10;
        this.accent = switch (menu.type) {
            case ITEM -> 0xFFE08A2B;
            case FLUID -> 0xFF2F7FE0;
            case ENERGY -> 0xFFD83A3A;
            case UNIVERSAL -> 0xFFA77BE8;
            case CHEMICAL -> 0xFF7CD957;
        };
    }

    @Override
    protected void init() {
        super.init();
        channelButtons.clear();
        fields.clear();
        int x = leftPos + SETTINGS_L + 2;
        int y = topPos + PANEL_TOP + 12;
        boolean extract = menu.extracting();
        redstoneButton = addRenderableWidget(new IconButton(x, y, 20,
                () -> "redstone_" + key(menu.redstone()), accent, b -> press(PipeMenu.BTN_REDSTONE)));
        distributionButton = addRenderableWidget(new IconButton(x + 22, y, 20,
                () -> "distribution_" + key(menu.distribution()), accent, b -> press(PipeMenu.BTN_DISTRIBUTION)));
        redstoneButton.visible = extract;
        distributionButton.visible = extract;
        clearButton = addRenderableWidget(new IconButton(x, extract ? y + 22 : y, 20,
                () -> "clear", accent, b -> clickClear())).warnWhen(this::clearArmed);
        overflowButton = addRenderableWidget(new IconButton(x + 22, y, 20, () -> "overflow", accent,
                () -> !menu.overflow(), b -> press(PipeMenu.BTN_OVERFLOW)));
        overflowButton.visible = !extract;
        signalButton = addRenderableWidget(new IconButton(x + 22, y + 22, 20,
                () -> "signal_" + key(menu.signal()), accent, b -> press(PipeMenu.BTN_SIGNAL)));
        signalButton.visible = extract;
        prevPageButton = addRenderableWidget(new IconButton(leftPos + FILTER_R - 24, topPos + PANEL_TOP + 2, 10,
                () -> "page_prev", accent, b -> press(PipeMenu.BTN_PREV_PAGE)));
        nextPageButton = addRenderableWidget(new IconButton(leftPos + FILTER_R - 13, topPos + PANEL_TOP + 2, 10,
                () -> "page_next", accent, b -> press(PipeMenu.BTN_NEXT_PAGE)));
        upgradeHelp = addRenderableWidget(new IconButton(leftPos + UPGRADE_R - 11, topPos + PANEL_TOP + 2, 9,
                () -> "help", accent, b -> {}));

        // values row
        int fx = SETTINGS_L + 3;
        int fy = topPos + VALUES_TOP + 10;
        if (!extract) {
            addField(fx, fy, -SideConfig.MAX_PRIORITY, SideConfig.MAX_PRIORITY, menu::priority, PipeMenu.FIELD_PRIORITY,
                    "gui.flowline.value.priority", "gui.flowline.value.priority.desc");
            fx += FIELD_W + 4;
        }
        if (menu.type != PipeType.CHEMICAL) {
            addField(fx, fy, 0, SideConfig.MAX_AMOUNT, menu::limit, PipeMenu.FIELD_LIMIT,
                    extract ? "gui.flowline.value.keep" : "gui.flowline.value.max",
                    extract ? "gui.flowline.value.keep.desc" : "gui.flowline.value.max.desc");
            fx += FIELD_W + 4;
        }
        if (extract && menu.type.movesEnergy()) {
            addField(fx, fy, 0, SideConfig.MAX_AMOUNT, menu::rate, PipeMenu.FIELD_RATE,
                    "gui.flowline.value.rate", "gui.flowline.value.rate.desc");
        }
        if (menu.type.hasChannels()) {
            String[] names = {"channel_items", "channel_fluids", "channel_energy"};
            for (int i = 0; i < 3; i++) {
                int bit = 1 << i;
                String icon = names[i];
                int id = PipeMenu.BTN_CHANNEL + i;
                channelButtons.add(addRenderableWidget(new IconButton(leftPos + UPGRADE_R - 44 + i * 15, fy - 1, 13,
                        () -> icon, accent, () -> (menu.channels() & bit) == 0, b -> press(id))));
            }
        }
    }

    private void addField(int x, int y, int min, int max, java.util.function.IntSupplier value, int field, String label,
                          String help) {
        NumberBox box = new NumberBox(font, leftPos + x, y, FIELD_W, min, max, value,
                v -> ModNetwork.sendToServer(new SetSideValuePayload(menu.containerId, field, v)));
        addRenderableWidget(box);
        fields.add(new Field(box, label, help, x));
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** 1000 mB -> "1B", 1500 mB -> "1500mB". */
    private static String buckets(int millibuckets) {
        return millibuckets % 1000 == 0 ? millibuckets / 1000 + "B" : millibuckets + "mB";
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    // ---- input --------------------------------------------------------------------------------------------

    /**
     * Filter slots with an empty hand: left click opens the rule library, shift + left click removes the rule.
     * Clicking with an item in hand goes to the menu, which turns the item into a rule.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Field field : fields) {
            if (!field.box().isMouseOver(mouseX, mouseY)) field.box().setFocused(false);
        }
        if (button == 1 && redstoneButton.visible && redstoneButton.isMouseOver(mouseX, mouseY)) {
            press(PipeMenu.BTN_REDSTONE_BACK);
            return true;
        }
        if (button == 1 && signalButton.visible && signalButton.isMouseOver(mouseX, mouseY)) {
            press(PipeMenu.BTN_SIGNAL_BACK);
            return true;
        }
        if (button == 1 && distributionButton.visible && distributionButton.isMouseOver(mouseX, mouseY)) {
            press(PipeMenu.BTN_DISTRIBUTION_BACK);
            return true;
        }
        if (hoveredSlot instanceof PipeMenu.GhostSlot ghost && menu.getCarried().isEmpty() && minecraft != null) {
            if (button == 0 && hasShiftDown()) {
                if (menu.clientEntry(ghost.getContainerSlot()) != null) {
                    ModNetwork.sendToServer(
                            new SetFilterEntryPayload(menu.containerId, ghost.filterIndex(), Optional.empty()));
                }
            } else if (button == 0) {
                minecraft.setScreen(new RuleEditorScreen(this, menu, ghost.filterIndex(),
                        menu.clientEntry(ghost.getContainerSlot()), accent));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Typing into a number field must not close the screen or swap hotbar items. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Field field : fields) {
            if (field.box().isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                    field.box().setFocused(false);
                    setFocused(null);
                } else {
                    field.box().keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Mouse wheel over the filter panel flips filter pages; over a number field it steps the number. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (Field field : fields) {
            if (field.box().isMouseOver(mouseX, mouseY) && scrollY != 0) {
                field.box().step(scrollY);
                return true;
            }
        }
        if (menu.hasFilter() && menu.pageCount() > 1 && scrollY != 0
                && isHovering(FILTER_L, PANEL_TOP, FILTER_R - FILTER_L, PANEL_BOTTOM - PANEL_TOP, mouseX, mouseY)) {
            int target = menu.page() + (scrollY < 0 ? 1 : -1);
            if (target >= 0 && target < menu.pageCount()) {
                press(scrollY < 0 ? PipeMenu.BTN_NEXT_PAGE : PipeMenu.BTN_PREV_PAGE);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean filter = menu.hasFilter();
        clearButton.visible = filter;
        boolean paged = filter && menu.pageCount() > 1;
        prevPageButton.visible = paged;
        nextPageButton.visible = paged;
        prevPageButton.active = menu.page() > 0;
        nextPageButton.active = menu.page() < menu.pageCount() - 1;
        fields.forEach(field -> field.box().follow());

        renderBackground(graphics);
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
        graphics.fill(l + SETTINGS_L, t + VALUES_TOP, l + UPGRADE_R, t + VALUES_BOTTOM, PANEL_EDGE);
        graphics.fill(l + SETTINGS_L + 1, t + VALUES_TOP + 1, l + UPGRADE_R - 1, t + VALUES_BOTTOM - 1, PANEL);

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = l + slot.x;
            int y = t + slot.y;
            boolean upgrade = slot instanceof PipeMenu.UpgradeSlot;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, upgrade && slot.hasItem() ? accent : SLOT_EDGE);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
        }

        // header icon
        graphics.renderItem(pipeStack(), l + 6, t + 6);

        if (!menu.hasFilter()) {
            int cx = l + (FILTER_L + FILTER_R) / 2;
            smallCentered(graphics, Component.translatable("gui.flowline.no_filter"), cx, t + 66, MUTED);
        } else if (menu.pageCount() > 1) {
            // page dots in the strip right of the grid
            int x = l + FILTER_R - 4;
            for (int i = 0; i < menu.pageCount(); i++) {
                int y = t + PipeMenu.FILTER_Y + 2 + i * 5;
                graphics.fill(x, y, x + 2, y + 3, i == menu.page() ? accent : PANEL_EDGE);
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
                Component.translatable(menu.extracting() ? "mode.flowline.extract" : "mode.flowline.insert"));
        small(graphics, sub, 26, 18, MUTED);

        // badge: stack multiplier and current interval (extract), priority (insert)
        String amount = menu.type.movesItems() ? Integer.toString(menu.itemsPerOperation())
                : menu.type.movesFluids() ? buckets(menu.fluidPerOperation())
                : menu.type.movesChemicals() ? buckets(menu.chemicalPerOperation())
                : "x" + menu.multiplier();
        String badge = !menu.extracting() ? "P " + menu.priority()
                : menu.redstone() == com.knozyy.flowline.pipe.RedstoneMode.PULSE ? amount + " · ⚡"
                : amount + " · " + (menu.sleeping() ? "zZ" : menu.interval() + "t");
        badgeW = font.width(badge) + 8;
        badgeX = imageWidth - 7 - badgeW;
        boolean boosted = menu.extracting() ? menu.speedCount() > 0 || menu.stackCount() > 0 : menu.priority() != 0;
        graphics.fill(badgeX, BADGE_Y, badgeX + badgeW, BADGE_Y + BADGE_H, boosted ? accent : PANEL_EDGE);
        graphics.drawString(font, badge, badgeX + 4, BADGE_Y + 2, TEXT, false);

        small(graphics, playerInventoryTitle, 8, inventoryLabelY, MUTED);

        // panel captions
        small(graphics, caption("gui.flowline.section.settings"), SETTINGS_L + 4, PANEL_TOP + 4, MUTED);
        small(graphics, caption("gui.flowline.section.filter"), FILTER_L + 4, PANEL_TOP + 4, MUTED);
        small(graphics, caption("gui.flowline.section.upgrade"), UPGRADE_L + 3, PANEL_TOP + 4, MUTED);
        for (Field field : fields) small(graphics, caption(field.label()), field.x(), VALUES_TOP + 3, MUTED);
        if (!channelButtons.isEmpty()) {
            small(graphics, caption("gui.flowline.value.channels"), UPGRADE_R - 44, VALUES_TOP + 3, MUTED);
        }

        renderRuleMarks(graphics);
    }

    /** Small marks over filter slots: "#" for tags, a purple corner for NBT, a red bar for blocking rules. */
    private void renderRuleMarks(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        for (Slot slot : menu.slots) {
            if (!(slot instanceof PipeMenu.GhostSlot ghost) || !ghost.isActive()) continue;
            FilterEntry entry = menu.clientEntry(ghost.getContainerSlot());
            if (entry == null) continue;
            int x = slot.x, y = slot.y;
            if (entry.isFluidRule(menu.type)) FluidIcon.draw(graphics, entry.displayFluid(), x, y, 16);
            if (!entry.tags().isEmpty()) {
                small(graphics, Component.literal("#" + (entry.tags().size() > 1 ? entry.tags().size() : "")), x, y,
                        0xFF7FD3FF);
            } else if (entry.mod().isPresent()) {
                small(graphics, Component.literal("@"), x, y, 0xFFFFC857);
            }
            if (entry.name().isPresent()) small(graphics, Component.literal("Aa"), x + 8, y + 9, 0xFF9CF0B0);
            if (entry.hasDurability()) graphics.fill(x, y + 12, x + 2, y + 14, 0xFF4AE6F0);
            if (entry.nbt().isPresent()) graphics.fill(x + 13, y, x + 16, y + 3, 0xFFB86BFF);
            if (entry.invert()) graphics.fill(x, y + 14, x + 16, y + 16, 0xFFE04848);
        }
        graphics.pose().popPose();
    }

    private static Component caption(String key) {
        return Component.literal(Component.translatable(key).getString().toUpperCase(Locale.ROOT));
    }

    private ItemStack pipeStack() {
        return switch (menu.type) {
            case ITEM -> new ItemStack(ModItems.ITEM_PIPE.get());
            case FLUID -> new ItemStack(ModItems.FLUID_PIPE.get());
            case ENERGY -> new ItemStack(ModItems.ENERGY_PIPE.get());
            case UNIVERSAL -> new ItemStack(ModItems.UNIVERSAL_PIPE.get());
            case CHEMICAL -> ModItems.CHEMICAL_PIPE == null ? ItemStack.EMPTY : new ItemStack(ModItems.CHEMICAL_PIPE.get());
        };
    }

    // ---- tooltips -----------------------------------------------------------------------------------------

    /** Filter slots describe their rule instead of the displayed item. */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot instanceof PipeMenu.GhostSlot ghost && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, ruleTooltip(menu.clientEntry(ghost.getContainerSlot())), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private List<Component> ruleTooltip(@Nullable FilterEntry entry) {
        List<Component> lines = new ArrayList<>();
        if (entry == null) {
            lines.add(Component.translatable("gui.flowline.rule.empty"));
            lines.add(Component.translatable("gui.flowline.rule.empty_hint").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (entry.item().isPresent() && entry.isFluidRule(menu.type)) {
            lines.add(entry.displayFluid().getFluidType().getDescription().copy());
            lines.add(Component.literal(entry.item().get()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (entry.item().isPresent()) {
            lines.add(entry.displayStack(menu.type, menu.registries()).getHoverName().copy());
            lines.add(Component.literal(entry.item().get()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (entry.tags().isEmpty() && entry.mod().isEmpty() && entry.name().isEmpty() && !entry.hasDurability()) {
            lines.add(Component.translatable("gui.flowline.rule.any"));
        }
        if (menu.type == PipeType.UNIVERSAL) {
            lines.add(Component.translatable(entry.fluid() ? "gui.flowline.rule.kind_fluid" : "gui.flowline.rule.kind_item")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        entry.mod().ifPresent(mod -> lines.add(Component.translatable("gui.flowline.rule.mod",
                Component.literal("@" + mod).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY)));
        entry.name().ifPresent(name -> lines.add(Component.translatable("gui.flowline.rule.name",
                Component.literal(name).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY)));
        if (entry.hasDurability()) {
            lines.add(Component.translatable("gui.flowline.rule.durability", entry.minDurability(), entry.maxDurability())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (entry.amount() > 0 && !entry.invert()) {
            lines.add(Component.translatable(menu.extracting() ? "gui.flowline.rule.amount.keep"
                    : "gui.flowline.rule.amount.max", entry.amount()).withStyle(ChatFormatting.YELLOW));
        }
        if (!entry.tags().isEmpty()) {
            lines.add(Component.translatable(entry.allTags() ? "gui.flowline.rule.tags_all" : "gui.flowline.rule.tags_any")
                    .withStyle(ChatFormatting.GRAY));
            int shown = Math.min(6, entry.tags().size());
            for (int i = 0; i < shown; i++) {
                lines.add(Component.literal("  #" + entry.tags().get(i)).withStyle(s -> s.withColor(accent)));
            }
            if (entry.tags().size() > shown) {
                lines.add(Component.translatable("gui.flowline.rule.more", entry.tags().size() - shown)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        entry.nbt().ifPresent(nbt -> {
            String text = nbt.toString();
            if (text.length() > 48) text = text.substring(0, 45) + "...";
            lines.add(Component.translatable(entry.exactNbt() ? "gui.flowline.rule.nbt_exact" : "gui.flowline.rule.nbt_contains",
                    Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
        });
        lines.add(entry.invert()
                ? Component.translatable("gui.flowline.rule.blocks").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.flowline.rule.allows").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("gui.flowline.rule.hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = null;
        for (Field field : fields) {
            if (field.box().isHovered()) {
                lines = List.of(Component.translatable(field.label()),
                        Component.translatable(field.help()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.flowline.value.hint").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        for (int i = 0; i < channelButtons.size(); i++) {
            if (channelButtons.get(i).isHovered()) {
                boolean on = (menu.channels() & (1 << i)) != 0;
                lines = List.of(Component.translatable("gui.flowline.channel." + i),
                        Component.translatable(on ? "gui.flowline.channel.on" : "gui.flowline.channel.off")
                                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        if (lines != null) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (redstoneButton.visible && redstoneButton.isHovered()) {
            lines = describe("gui.flowline.redstone", "redstone.flowline." + key(menu.redstone()), true);
        } else if (distributionButton.visible && distributionButton.isHovered()) {
            lines = describe("gui.flowline.distribution", "distribution.flowline." + key(menu.distribution()), true);
        } else if (prevPageButton.visible && (prevPageButton.isHovered() || nextPageButton.isHovered())) {
            lines = List.of(Component.translatable("gui.flowline.filter_page", menu.page() + 1, menu.pageCount()),
                    Component.translatable("gui.flowline.filter_capacity", menu.capacity())
                            .withStyle(ChatFormatting.GRAY));
        } else if (signalButton.visible && signalButton.isHovered()) {
            lines = describe("gui.flowline.signal", "signal.flowline." + key(menu.signal()), true);
        } else if (overflowButton.visible && overflowButton.isHovered()) {
            lines = List.of(Component.translatable(menu.overflow() ? "gui.flowline.overflow.on" : "gui.flowline.overflow.off"),
                    Component.translatable("gui.flowline.overflow.desc").withStyle(ChatFormatting.GRAY));
        } else if (clearButton.visible && clearButton.isHovered()) {
            lines = clearArmed()
                    ? List.of(Component.translatable("gui.flowline.clear.confirm").withStyle(ChatFormatting.RED))
                    : List.of(Component.translatable("gui.flowline.clear"),
                            Component.translatable("gui.flowline.clear.desc").withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.flowline.clear.how").withStyle(ChatFormatting.DARK_GRAY));
        } else if (upgradeHelp.isHovered()) {
            lines = upgradeLegend();
        } else if (isHovering(badgeX, BADGE_Y, badgeW, BADGE_H, mouseX, mouseY)) {
            lines = menu.extracting() ? pacingTooltip() : List.of(
                    Component.translatable("gui.flowline.priority", menu.priority()),
                    Component.translatable("gui.flowline.value.priority.desc").withStyle(ChatFormatting.GRAY));
        } else if (hoveredSlot instanceof PipeMenu.UpgradeSlot && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            lines = List.of(Component.translatable("gui.flowline.upgrade_slot"),
                    Component.translatable("gui.flowline.upgrade_slot.desc").withStyle(ChatFormatting.GRAY));
        }
        if (lines != null) graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Clear filter: a second click within three seconds, or Shift + click, removes every rule. */
    private void clickClear() {
        if (hasShiftDown() || clearArmed()) {
            clearArmedUntil = 0;
            press(PipeMenu.BTN_CLEAR);
        } else {
            clearArmedUntil = Util.getMillis() + 3000;
        }
    }

    private boolean clearArmed() {
        return Util.getMillis() < clearArmedUntil;
    }

    /** What each upgrade does, and what the installed ones add up to on this side. */
    private List<Component> upgradeLegend() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.flowline.upgrades.title", SideConfig.UPGRADE_SLOTS));
        if (!menu.extracting()) {
            lines.add(Component.translatable("gui.flowline.upgrades.insert").withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("item.flowline.speed_upgrade").withStyle(s -> s.withColor(UpgradeItem.SPEED_COLOR)));
        UpgradeItem.effectLines(UpgradeType.SPEED).forEach(line -> lines.add(indent(line)));
        lines.add(Component.translatable("item.flowline.stack_upgrade").withStyle(s -> s.withColor(UpgradeItem.STACK_COLOR)));
        UpgradeItem.effectLines(UpgradeType.STACK).forEach(line -> lines.add(indent(line)));
        lines.add(Component.translatable("item.flowline.filter_upgrade").withStyle(s -> s.withColor(UpgradeItem.FILTER_COLOR)));
        UpgradeItem.effectLines(UpgradeType.FILTER).forEach(line -> lines.add(indent(line)));
        lines.add(Component.translatable("item.flowline.knozy_upgrade").withStyle(s -> s.withColor(UpgradeItem.KNOZY_COLOR)));
        lines.add(indent(Component.translatable("item.flowline.knozy_upgrade.desc").withStyle(ChatFormatting.GRAY)));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.flowline.upgrades.now", menu.startInterval(), menu.multiplier(),
                menu.capacity()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Component indent(Component line) {
        return Component.literal("  ").append(line);
    }

    private List<Component> pacingTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.flowline.pacing.title"));
        lines.add(Component.translatable("gui.flowline.pacing.counts", menu.speedCount(), menu.stackCount(),
                menu.filterCount())
                .withStyle(ChatFormatting.GRAY));
        if (menu.type.movesItems()) {
            lines.add(Component.translatable("gui.flowline.pacing.items", menu.itemsPerOperation())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (menu.type.movesFluids()) {
            lines.add(Component.translatable("gui.flowline.pacing.fluid", menu.fluidPerOperation())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (menu.type.movesChemicals()) {
            lines.add(Component.translatable("gui.flowline.pacing.chemical", menu.chemicalPerOperation())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (menu.type.movesEnergy()) {
            lines.add(Component.translatable("gui.flowline.pacing.amount", menu.multiplier())
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.flowline.pacing.interval", menu.interval(), menu.startInterval(),
                menu.minInterval()).withStyle(ChatFormatting.GRAY));
        lines.add(menu.sleeping()
                ? Component.translatable("gui.flowline.pacing.sleeping").withStyle(ChatFormatting.GOLD)
                : Component.translatable("gui.flowline.pacing.hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** "Label: Value", a grey description line and, when disabled, why. */
    private List<Component> describe(String labelKey, String valueKey, boolean enabled) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(labelKey, Component.translatable(valueKey).withStyle(s -> s.withColor(accent))));
        lines.add(Component.translatable(valueKey + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.flowline.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
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
