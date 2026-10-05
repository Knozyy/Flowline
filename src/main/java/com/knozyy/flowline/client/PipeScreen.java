package com.knozyy.flowline.client;

import com.knozyy.flowline.client.ui.FlatButton;
import com.knozyy.flowline.client.ui.RuleText;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.client.ui.Ui;
import com.knozyy.flowline.compat.ChemicalCompat;
import net.minecraft.resources.ResourceLocation;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.RuleFromCarriedPayload;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.network.SetSideValuePayload;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideStatus;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Configuration screen of one pipe side: a header with the side's status and tabs for the pipe's other sides, a
 * list of settings on the left, the filter's rules on the right (pipes without a filter use the whole width), then
 * the player inventory with the upgrade slots beside it. Drawn without textures, in {@link Theme}'s colours.
 */
public class PipeScreen extends AbstractContainerScreen<PipeMenu> {
    private static final int W = PipeMenu.WIDTH, H = PipeMenu.HEIGHT;
    private static final int TABS_Y = 28, BODY_T = 46, BODY_B = 146;
    private static final int LEFT_L = 6, LEFT_R = 138, RIGHT_L = 142, RIGHT_R = W - 6;
    private static final int GROUP_H = 10, ROW_H = 11, VALUE_W = 70;
    private static final int LIST_T = BODY_T + 24, RULE_H = 21;
    private static final int UPGRADE_TEXT_Y = PipeMenu.UPGRADE_Y + 40;
    private static final Direction[] TAB_ORDER = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
            Direction.UP, Direction.DOWN};

    private final int accent;
    /** Widgets with an explanation shown while hovered. */
    private final List<Tip> tips = new ArrayList<>();
    private final List<NumberBox> boxes = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    /** First rule row shown; kept while the rule editor is open. */
    private int ruleScroll = 0;
    /** Until when (ms) the clear button is armed: the first click only arms it, so rules are not lost to a misclick. */
    private long clearArmedUntil = 0;
    /** The current mouse press started on the rule list, so its release and drags belong to the list too. */
    private boolean listPress = false;
    private IconButton clearButton;

    private record Tip(AbstractWidget widget, Supplier<List<Component>> lines) {}

    /** A group caption ({@code row} false) or a row label, with the tooltip of the row. */
    private record Line(Component text, int y, boolean row, @Nullable Supplier<List<Component>> tip) {}

    /** One entry of the rule list: a rule position, or {@code -1} for the "add rule" row. */
    private record Item(int index, @Nullable FilterEntry rule) {}

    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = -1000;
        this.accent = Theme.accent(menu.type);
    }

    // ---- layout -------------------------------------------------------------------------------------------

    private int settingsRight() {
        return menu.hasFilter() ? LEFT_R : RIGHT_R;
    }

    @Override
    protected void init() {
        super.init();
        tips.clear();
        boxes.clear();
        lines.clear();
        int y = BODY_T + 2;
        if (menu.extracting()) {
            y = group(y, "gui.flowline.group.flow");
            y = cycleRow(y, "gui.flowline.row.distribution",
                    () -> Component.translatable("distribution.flowline." + key(menu.distribution()) + ".short"),
                    PipeMenu.BTN_DISTRIBUTION, PipeMenu.BTN_DISTRIBUTION_BACK,
                    () -> describe("gui.flowline.distribution", "distribution.flowline." + key(menu.distribution())));
            if (menu.type.hasChannels()) y = channelRow(y);
            y = group(y, "gui.flowline.group.redstone");
            y = cycleRow(y, "gui.flowline.row.redstone",
                    () -> Component.translatable("redstone.flowline." + key(menu.redstone()) + ".short"),
                    PipeMenu.BTN_REDSTONE, PipeMenu.BTN_REDSTONE_BACK,
                    () -> describe("gui.flowline.redstone", "redstone.flowline." + key(menu.redstone())));
            y = cycleRow(y, "gui.flowline.row.signal",
                    () -> Component.translatable("signal.flowline." + key(menu.signal()) + ".short"),
                    PipeMenu.BTN_SIGNAL, PipeMenu.BTN_SIGNAL_BACK,
                    () -> describe("gui.flowline.signal", "signal.flowline." + key(menu.signal())));
            boolean keep = menu.type != PipeType.CHEMICAL, rate = menu.type.movesEnergy();
            if (keep || rate) y = group(y, "gui.flowline.group.limits");
            if (keep) {
                y = numberRow(y, "gui.flowline.row.keep", 0, SideConfig.MAX_AMOUNT, menu::limit, PipeMenu.FIELD_LIMIT,
                        "gui.flowline.value.keep.desc");
            }
            if (rate) {
                numberRow(y, "gui.flowline.row.rate", 0, SideConfig.MAX_AMOUNT, menu::rate, PipeMenu.FIELD_RATE,
                        "gui.flowline.value.rate.desc");
            }
        } else {
            y = group(y, "gui.flowline.group.target");
            y = priorityRow(y);
            if (menu.type != PipeType.CHEMICAL) {
                y = numberRow(y, "gui.flowline.row.max", 0, SideConfig.MAX_AMOUNT, menu::limit, PipeMenu.FIELD_LIMIT,
                        "gui.flowline.value.max.desc");
            }
            y = cycleRow(y, "gui.flowline.row.overflow",
                    () -> Component.translatable(menu.overflow() ? "gui.flowline.overflow.short_on"
                            : "gui.flowline.overflow.short_off"),
                    PipeMenu.BTN_OVERFLOW, PipeMenu.BTN_OVERFLOW,
                    () -> List.of(Component.translatable(menu.overflow() ? "gui.flowline.overflow.on"
                                    : "gui.flowline.overflow.off"),
                            Component.translatable("gui.flowline.overflow.desc").withStyle(ChatFormatting.GRAY)));
            if (menu.type.hasChannels()) {
                y = group(y, "gui.flowline.group.flow");
                channelRow(y);
            }
        }
        if (menu.hasFilter()) {
            clearButton = addRenderableWidget(new IconButton(leftPos + RIGHT_R - 14, topPos + BODY_T + 3, 10,
                    () -> "clear", accent, b -> clickClear())).warnWhen(this::clearArmed);
            tips.add(new Tip(clearButton, () -> clearArmed()
                    ? List.of(Component.translatable("gui.flowline.clear.confirm").withStyle(ChatFormatting.RED))
                    : List.of(Component.translatable("gui.flowline.clear"),
                            Component.translatable("gui.flowline.clear.desc").withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.flowline.clear.how").withStyle(ChatFormatting.DARK_GRAY))));
        }
    }

    private int group(int y, String key) {
        lines.add(new Line(Component.translatable(key), y, false, null));
        return y + GROUP_H;
    }

    private int valueX() {
        return leftPos + settingsRight() - 4 - VALUE_W;
    }

    private int cycleRow(int y, String label, Supplier<Component> value, int next, int back,
                         Supplier<List<Component>> tip) {
        lines.add(new Line(Component.translatable(label), y, true, tip));
        FlatButton button = addRenderableWidget(new FlatButton(valueX(), topPos + y, VALUE_W, ROW_H,
                () -> Component.literal("‹ ").append(value.get()).append(" ›"), accent, b -> press(next))
                .onBack(() -> press(back)));
        tips.add(new Tip(button, tip));
        return y + ROW_H;
    }

    private int numberRow(int y, String label, int min, int max, IntSupplier value, int field, String help) {
        Supplier<List<Component>> tip = () -> List.of(Component.translatable(label),
                Component.translatable(help).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.flowline.value.hint").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(new Line(Component.translatable(label), y, true, tip));
        addBox(valueX(), y, VALUE_W, min, max, value, field, tip);
        return y + ROW_H;
    }

    private int priorityRow(int y) {
        Supplier<List<Component>> tip = () -> List.of(Component.translatable("gui.flowline.row.priority"),
                Component.translatable("gui.flowline.value.priority.desc").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.flowline.value.hint").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(new Line(Component.translatable("gui.flowline.row.priority"), y, true, tip));
        int x = valueX();
        FlatButton minus = addRenderableWidget(new FlatButton(x, topPos + y, 11, ROW_H, () -> Component.literal("−"),
                accent, b -> sendValue(PipeMenu.FIELD_PRIORITY, menu.priority() - step())));
        FlatButton plus = addRenderableWidget(new FlatButton(x + VALUE_W - 11, topPos + y, 11, ROW_H,
                () -> Component.literal("+"), accent, b -> sendValue(PipeMenu.FIELD_PRIORITY, menu.priority() + step())));
        tips.add(new Tip(minus, tip));
        tips.add(new Tip(plus, tip));
        addBox(x + 13, y, VALUE_W - 26, -SideConfig.MAX_PRIORITY, SideConfig.MAX_PRIORITY, menu::priority,
                PipeMenu.FIELD_PRIORITY, tip);
        return y + ROW_H;
    }

    private static int step() {
        return hasControlDown() ? 100 : hasShiftDown() ? 10 : 1;
    }

    private void sendValue(int field, int value) {
        int bound = field == PipeMenu.FIELD_PRIORITY ? SideConfig.MAX_PRIORITY : SideConfig.MAX_AMOUNT;
        int min = field == PipeMenu.FIELD_PRIORITY ? -bound : 0;
        ModNetwork.sendToServer(new SetSideValuePayload(menu.containerId, field, Math.max(min, Math.min(bound, value))));
    }

    /** A number field inside a well spanning {@code width} pixels from {@code x}. */
    private void addBox(int x, int y, int width, int min, int max, IntSupplier value, int field,
                        Supplier<List<Component>> tip) {
        NumberBox box = new NumberBox(font, x + 3, topPos + y + 2, width - 6, min, max, value,
                v -> ModNetwork.sendToServer(new SetSideValuePayload(menu.containerId, field, v)));
        box.setBordered(false);
        box.setTextColor(Theme.TEXT);
        addRenderableWidget(box);
        boxes.add(box);
        tips.add(new Tip(box, tip));
    }

    private int channelRow(int y) {
        Supplier<List<Component>> tip = () -> List.of(Component.translatable("gui.flowline.value.channels"),
                Component.translatable("gui.flowline.channels.desc").withStyle(ChatFormatting.GRAY));
        lines.add(new Line(Component.translatable("gui.flowline.row.channels"), y, true, tip));
        String[] icons = {"channel_items", "channel_fluids", "channel_energy"};
        int x = leftPos + settingsRight() - 4 - 3 * 12 + 1;
        for (int i = 0; i < 3; i++) {
            int bit = 1 << i, id = PipeMenu.BTN_CHANNEL + i, channel = i;
            String icon = icons[i];
            IconButton button = addRenderableWidget(new IconButton(x + i * 12, topPos + y, 11, () -> icon, accent,
                    () -> (menu.channels() & bit) == 0, b -> press(id)));
            tips.add(new Tip(button, () -> {
                boolean on = (menu.channels() & bit) != 0;
                return List.of(Component.translatable("gui.flowline.channel." + channel),
                        Component.translatable(on ? "gui.flowline.channel.on" : "gui.flowline.channel.off")
                                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            }));
        }
        return y + ROW_H;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    // ---- rule list ----------------------------------------------------------------------------------------

    /** Rules in the usable positions, then the "add rule" row while there is room. */
    private List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < Math.min(menu.capacity(), menu.clientFilter().size()); i++) {
            FilterEntry rule = menu.clientEntry(i);
            if (rule != null) items.add(new Item(i, rule));
        }
        if (!items.isEmpty() && menu.firstFreeClient() >= 0) items.add(new Item(-1, null));
        return items;
    }

    private static int visibleRules() {
        return (BODY_B - 2 - LIST_T) / RULE_H;
    }

    private int clampScroll(int scroll, int count) {
        return Math.max(0, Math.min(scroll, Math.max(0, count - visibleRules())));
    }

    private boolean overList(double mouseX, double mouseY) {
        return menu.hasFilter() && Ui.in(mouseX, mouseY, leftPos + RIGHT_L + 1, topPos + LIST_T,
                leftPos + RIGHT_R - 1, topPos + BODY_B - 1);
    }

    /** The list entry under the mouse; an empty list counts as one big "add rule" entry. */
    @Nullable
    private Item itemAt(double mouseX, double mouseY) {
        if (!overList(mouseX, mouseY)) return null;
        List<Item> items = items();
        if (items.isEmpty()) return new Item(-1, null);
        int row = (int) (mouseY - topPos - LIST_T) / RULE_H;
        if (row >= visibleRules()) return null;
        int index = row + clampScroll(ruleScroll, items.size());
        return index < items.size() ? items.get(index) : null;
    }

    /** Whether the mouse is on the Allow/Block chip of the rule row it is over. */
    private boolean overChip(double mouseX, double mouseY, Item item) {
        if (item.rule() == null) return false;
        int rowTop = topPos + LIST_T + (int) (mouseY - topPos - LIST_T) / RULE_H * RULE_H;
        int[] chip = chipRect(item.rule(), leftPos + RIGHT_R, rowTop);
        return Ui.in(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3]);
    }

    /** Screen area that accepts an ingredient dragged from JEI or EMI, or null while the filter is full. */
    @Nullable
    public int[] ruleDropArea() {
        if (!menu.hasFilter() || menu.firstFreeClient() < 0) return null;
        return new int[]{leftPos + RIGHT_L, topPos + BODY_T, RIGHT_R - RIGHT_L, BODY_B - BODY_T};
    }

    private void openEditor(int index, @Nullable FilterEntry rule) {
        if (minecraft == null || index < 0) return;
        minecraft.setScreen(new RuleEditorScreen(this, menu, index, rule, accent));
    }

    // ---- input --------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (NumberBox box : boxes) {
            if (!box.isMouseOver(mouseX, mouseY)) box.setFocused(false);
        }
        Direction tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            if (button == 0 && tab != menu.side && tabOpens(tab)) press(PipeMenu.BTN_SIDE + tab.get3DDataValue());
            return true;
        }
        if (overList(mouseX, mouseY)) {
            listPress = true;
            Item item = itemAt(mouseX, mouseY);
            if (button == 0 && item != null && menu.getCarried().isEmpty() && !hasShiftDown()
                    && overChip(mouseX, mouseY, item)) {
                ModNetwork.sendToServer(new SetFilterEntryPayload(menu.containerId, item.index(),
                        Optional.of(item.rule().withInvert(!item.rule().invert()))));
            } else {
                clickList(item, button);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * With a stack in hand a click turns it into a rule (in place of the clicked one, or in the first free spot).
     * With an empty hand: click edits, Shift + click or right-click removes, the "add rule" row opens a new rule.
     */
    private void clickList(@Nullable Item item, int button) {
        if (item == null || minecraft == null) return;
        if (!menu.getCarried().isEmpty()) {
            ModNetwork.sendToServer(new RuleFromCarriedPayload(menu.containerId, item.index()));
            return;
        }
        if (item.rule() == null) {
            if (button == 0) openEditor(menu.firstFreeClient(), null);
        } else if (button == 1 || button == 0 && hasShiftDown()) {
            ModNetwork.sendToServer(new SetFilterEntryPayload(menu.containerId, item.index(), Optional.empty()));
        } else if (button == 0) {
            openEditor(item.index(), item.rule());
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (listPress) {
            listPress = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (listPress) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Typing into a number field must not close the screen or swap hotbar items. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (NumberBox box : boxes) {
            if (box.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    box.setFocused(false);
                    setFocused(null);
                } else {
                    box.keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (NumberBox box : boxes) {
            if (box.isMouseOver(mouseX, mouseY) && scrollY != 0) {
                box.step(scrollY);
                return true;
            }
        }
        if (overList(mouseX, mouseY) && scrollY != 0) {
            ruleScroll = clampScroll(ruleScroll + (scrollY < 0 ? 1 : -1), items().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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

    // ---- side tabs ----------------------------------------------------------------------------------------

    @Nullable
    private Direction tabAt(double mouseX, double mouseY) {
        for (int i = 0; i < TAB_ORDER.length; i++) {
            int x = leftPos + 40 + i * 16;
            if (Ui.in(mouseX, mouseY, x, topPos + TABS_Y, x + 13, topPos + TABS_Y + 13)) return TAB_ORDER[i];
        }
        return null;
    }

    private Conn conn(Direction dir) {
        if (minecraft == null || minecraft.level == null) return Conn.NONE;
        BlockState state = minecraft.level.getBlockState(menu.pos);
        return state.getBlock() instanceof PipeBlock ? state.getValue(PipeBlock.prop(dir)) : Conn.NONE;
    }

    private boolean tabOpens(Direction dir) {
        return conn(dir).isEndpoint();
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        Ui.text(g, font, Component.translatable("gui.flowline.tabs"), leftPos + 8, topPos + TABS_Y + 3, Theme.MUTED);
        for (int i = 0; i < TAB_ORDER.length; i++) {
            Direction dir = TAB_ORDER[i];
            int l = leftPos + 40 + i * 16, t = topPos + TABS_Y, r = l + 13, b = t + 13;
            Conn conn = conn(dir);
            boolean current = dir == menu.side;
            Component letter = Component.translatable("gui.flowline.tab." + dir.getName());
            int color = Theme.TEXT;
            if (current) {
                g.fill(l, t, r, b, accent);
                color = Theme.ON_ACCENT;
            } else {
                if (conn.isEndpoint() && Ui.in(mouseX, mouseY, l, t, r, b)) g.fill(l, t, r, b, Theme.HOVER);
                switch (conn) {
                    case EXTRACT -> {
                        Ui.frame(g, l, t, r, b, Theme.TEXT);
                        Ui.frame(g, l + 1, t + 1, r - 1, b - 1, Theme.TEXT);
                    }
                    case ENDPOINT -> Ui.frame(g, l, t, r, b, Theme.EDGE);
                    case PIPE -> {
                        Ui.dashed(g, l, t, r, b, Theme.MUTED, 1);
                        color = Theme.MUTED;
                    }
                    case NONE -> {
                        Ui.dashed(g, l, t, r, b, Theme.LINE, 2);
                        color = Theme.MUTED;
                    }
                }
            }
            Ui.textCentered(g, font, letter, l + 7, t + 3, color);
        }
    }

    private List<Component> tabTooltip(Direction dir) {
        Conn conn = conn(dir);
        String state = switch (conn) {
            case EXTRACT -> "gui.flowline.tab.extract";
            case ENDPOINT -> "gui.flowline.tab.insert";
            case PIPE -> "gui.flowline.tab.pipe";
            case NONE -> "gui.flowline.tab.none";
        };
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.flowline.side_line", Component.translatable("direction.flowline." + dir.getName())));
        tip.add(Component.translatable(state).withStyle(ChatFormatting.GRAY));
        if (dir == menu.side) {
            tip.add(Component.translatable("gui.flowline.tab.current").withStyle(ChatFormatting.DARK_GRAY));
        } else if (conn.isEndpoint()) {
            tip.add(Component.translatable("gui.flowline.tab.open").withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boxes.forEach(NumberBox::follow);
        if (clearButton != null) clearButton.visible = menu.ruleCount() > 0;
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos;
        Ui.window(g, l, t, l + W, t + H);
        g.fill(l + 6, t + TABS_Y - 3, l + W - 6, t + TABS_Y - 2, Theme.LINE);
        g.fill(l + 6, t + TABS_Y + 15, l + W - 6, t + TABS_Y + 16, Theme.LINE);
        Ui.panel(g, l + LEFT_L, t + BODY_T, l + settingsRight(), t + BODY_B);
        if (menu.hasFilter()) Ui.panel(g, l + RIGHT_L, t + BODY_T, l + RIGHT_R, t + BODY_B);
        for (NumberBox box : boxes) {
            Ui.well(g, box.getX() - 3, box.getY() - 2, box.getX() + box.getWidth() + 3, box.getY() + box.getHeight() - 2);
        }
        for (Slot slot : menu.slots) {
            int x = l + slot.x, y = t + slot.y;
            boolean filledUpgrade = slot instanceof PipeMenu.UpgradeSlot && slot.hasItem();
            g.fill(x - 1, y - 1, x + 17, y + 17, filledUpgrade ? accent : Theme.LINE);
            g.fill(x, y, x + 16, y + 16, Theme.SLOT);
        }
        g.renderItem(pipeStack(), l + 8, t + 5);
        renderTabs(g, mouseX, mouseY);
        if (menu.hasFilter()) renderRules(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        renderHeader(g);
        int right = settingsRight();
        for (Line line : lines) {
            if (line.row()) {
                Ui.text(g, font, Ui.fit(font, line.text().getString(), right - 4 - VALUE_W - LEFT_L - 8),
                        LEFT_L + 4, line.y() + 2, Theme.TEXT);
            } else {
                Ui.text(g, font, line.text(), LEFT_L + 4, line.y() + 1, Theme.MUTED);
                g.fill(LEFT_L + 3, line.y() + 9, right - 3, line.y() + 10, Theme.LINE);
            }
        }
        if (menu.hasFilter()) renderFilterHeader(g);
        renderUpgradeText(g);
    }

    private void renderHeader(GuiGraphics g) {
        Component badge;
        Component reason;
        boolean working = false;
        if (menu.extracting()) {
            SideStatus status = menu.status();
            working = status == SideStatus.WORKING;
            badge = Component.translatable("gui.flowline.status." + key(status));
            reason = switch (status) {
                case WORKING -> Component.translatable("gui.flowline.status.pace", menu.interval(), amount());
                case WAITING -> Component.translatable(menu.redstone() == RedstoneMode.PULSE
                        ? "gui.flowline.status.waiting.pulse" : "gui.flowline.status.waiting.why");
                default -> Component.translatable("gui.flowline.status." + key(status) + ".why");
            };
        } else {
            badge = Component.translatable("gui.flowline.status.target");
            reason = Component.translatable("gui.flowline.priority", menu.priority());
        }
        int badgeW = font.width(badge) + 10;
        int badgeX = W - 8 - badgeW;
        if (working) {
            g.fill(badgeX, 5, badgeX + badgeW, 16, accent);
            Ui.text(g, font, badge, badgeX + 5, 7, Theme.ON_ACCENT);
        } else {
            Ui.frame(g, badgeX, 5, badgeX + badgeW, 16, Theme.MUTED);
            Ui.text(g, font, badge, badgeX + 5, 7, Theme.TEXT);
        }
        int reasonW = Math.min(font.width(reason), 120);
        Ui.text(g, font, Ui.fit(font, reason.getString(), 120), W - 8 - reasonW, 17, Theme.MUTED);

        int textW = Math.min(badgeX, W - 8 - reasonW) - 34;
        Component name = Component.translatable("block.flowline." + menu.type.getSerializedName() + "_pipe");
        Ui.text(g, font, Ui.fit(font, name.getString(), textW), 28, 5, Theme.TEXT);
        Component sub = Component.translatable("gui.flowline.side_line",
                Component.translatable("direction.flowline." + menu.side.getName()))
                .append(" · ")
                .append(Component.translatable(menu.extracting() ? "mode.flowline.extract" : "mode.flowline.insert"));
        Ui.text(g, font, Ui.fit(font, sub.getString(), textW), 28, 15, Theme.MUTED);
    }

    /** What one operation moves, e.g. "16 items", "1000 mB" or "x16". */
    private Component amount() {
        if (menu.type.movesItems()) return Component.translatable("gui.flowline.amount.items", menu.itemsPerOperation());
        if (menu.type.movesFluids()) return Component.translatable("gui.flowline.amount.mb", menu.fluidPerOperation());
        if (menu.type.movesChemicals()) return Component.translatable("gui.flowline.amount.mb", menu.chemicalPerOperation());
        return Component.translatable("gui.flowline.amount.energy", menu.multiplier());
    }

    private void renderFilterHeader(GuiGraphics g) {
        int count = menu.ruleCount();
        Ui.text(g, font, Component.translatable("gui.flowline.section.filter"), RIGHT_L + 4, BODY_T + 4, Theme.TEXT);
        Component counter = Component.literal(count + " / " + menu.capacity());
        Ui.textRight(g, font, counter, RIGHT_R - (count > 0 ? 18 : 5), BODY_T + 4, Theme.MUTED);
        boolean allow = false, block = false;
        for (Item item : items()) {
            if (item.rule() == null) continue;
            if (item.rule().invert()) block = true;
            else allow = true;
        }
        String mode = allow ? "gui.flowline.filter.allow_only" : block ? "gui.flowline.filter.block_only"
                : "gui.flowline.filter.none";
        Ui.text(g, font, Ui.fit(font, Component.translatable(mode).getString(), RIGHT_R - RIGHT_L - 8),
                RIGHT_L + 4, BODY_T + 14, Theme.MUTED);
    }

    private void renderRules(GuiGraphics g, int mouseX, int mouseY) {
        int l = leftPos + RIGHT_L, r = leftPos + RIGHT_R, top = topPos + LIST_T;
        List<Item> items = items();
        if (items.isEmpty()) {
            int b = topPos + BODY_B - 3;
            boolean full = menu.firstFreeClient() < 0;
            if (!full && overList(mouseX, mouseY)) g.fill(l + 3, top, r - 3, b, Theme.HOVER);
            Ui.dashed(g, l + 3, top, r - 3, b, Theme.EDGE, 2);
            int center = (l + r) / 2;
            Ui.textCentered(g, font, Component.translatable(full ? "gui.flowline.filter.full"
                    : "gui.flowline.filter.empty.title"), center, top + 22, Theme.TEXT);
            if (!full) {
                int lineY = top + 34;
                for (var line : font.split(Component.translatable("gui.flowline.filter.empty.hint"), r - l - 16)) {
                    g.drawString(font, line, center - font.width(line) / 2, lineY, Theme.MUTED, false);
                    lineY += 10;
                }
            }
            return;
        }
        ruleScroll = clampScroll(ruleScroll, items.size());
        int visible = visibleRules();
        for (int row = 0; row < visible && row + ruleScroll < items.size(); row++) {
            Item item = items.get(row + ruleScroll);
            int y = top + row * RULE_H;
            boolean hover = Ui.in(mouseX, mouseY, l + 1, y, r - 1, y + RULE_H);
            if (item.rule() == null) {
                if (hover) g.fill(l + 3, y + 1, r - 6, y + RULE_H - 1, Theme.HOVER);
                Ui.dashed(g, l + 3, y + 1, r - 6, y + RULE_H - 1, Theme.EDGE, 2);
                Ui.textCentered(g, font, Component.translatable("gui.flowline.filter.add"), (l + r - 3) / 2, y + 7,
                        Theme.MUTED);
                continue;
            }
            if (hover) g.fill(l + 1, y, r - 1, y + RULE_H, Theme.HOVER);
            if (row > 0) g.fill(l + 3, y, r - 6, y + 1, Theme.LINE);
            int[] chip = chipRect(item.rule(), r, y);
            renderRuleRow(g, item.rule(), l, r, y, Ui.in(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3]));
        }
        Ui.scrollbar(g, r - 4, top, topPos + BODY_B - 2, ruleScroll, items.size(), visible, accent);
    }

    private void renderRuleRow(GuiGraphics g, FilterEntry rule, int l, int r, int y, boolean chipHover) {
        if (rule.isFluidRule(menu.type)) {
            FluidIcon.draw(g, rule.displayFluid(), l + 3, y + 2, 16);
        } else {
            g.renderItem(rule.displayStack(menu.type, menu.registries()), l + 3, y + 2);
            if (rule.isChemicalRule(menu.type) && rule.item().isPresent()) {
                ResourceLocation chemical = ResourceLocation.tryParse(rule.item().get());
                int tint = chemical == null ? -1 : ChemicalCompat.tint(chemical);
                if (tint >= 0) g.fill(l + 12, y + 13, l + 19, y + 18, 0xFF000000 | tint);
            }
        }
        int[] chip = chipRect(rule, r, y);
        if (chipHover) g.fill(chip[0] - 1, chip[1] - 1, chip[2] + 1, chip[3] + 1, accent);
        Ui.chip(g, font, chipText(rule), chip[0], chip[1], rule.invert());
        int textL = l + 23;
        String title = RuleText.title(rule, menu.type, menu.registries()).getString();
        Ui.text(g, font, Ui.fit(font, title, chip[0] - 4 - textL), textL, y + 2, Theme.TEXT);
        String detail = RuleText.detail(rule, menu.type, menu.extracting()).getString();
        Ui.text(g, font, Ui.fit(font, detail, r - 8 - textL), textL, y + 12, Theme.MUTED);
    }

    private static Component chipText(FilterEntry rule) {
        return Component.translatable(rule.invert() ? "gui.flowline.chip.block" : "gui.flowline.chip.allow");
    }

    /** Screen rectangle of a rule row's Allow/Block chip, which switches the rule when clicked. */
    private int[] chipRect(FilterEntry rule, int r, int y) {
        int w = Ui.chipWidth(font, chipText(rule));
        return new int[]{r - 7 - w, y + 1, r - 7, y + 12};
    }

    private void renderUpgradeText(GuiGraphics g) {
        int installed = 0;
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            if (menu.slots.get(i).hasItem()) installed++;
        }
        int x = PipeMenu.UPGRADE_X - 1, width = RIGHT_R - x;
        Ui.text(g, font, Ui.fit(font, Component.translatable("gui.flowline.upgrades.count", installed,
                SideConfig.UPGRADE_SLOTS).getString(), width), x, UPGRADE_TEXT_Y, Theme.TEXT);
        List<Component> effects = new ArrayList<>();
        if (menu.extracting()) {
            effects.add(Component.translatable("gui.flowline.upgrades.start", menu.startInterval()));
            effects.add(Component.translatable("gui.flowline.upgrades.per_operation", amount()));
        } else {
            effects.add(Component.translatable("gui.flowline.upgrades.filter_only"));
            if (menu.hasFilter()) effects.add(Component.translatable("gui.flowline.upgrades.rules", menu.capacity()));
        }
        for (int i = 0; i < effects.size(); i++) {
            Ui.text(g, font, Ui.fit(font, effects.get(i).getString(), width), x, UPGRADE_TEXT_Y + 10 * (i + 1),
                    Theme.MUTED);
        }
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

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = hoveredTooltip(mouseX, mouseY);
        if (lines != null) {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Nullable
    private List<Component> hoveredTooltip(int mouseX, int mouseY) {
        for (Tip tip : tips) {
            if (tip.widget().visible && tip.widget().isHovered()) return tip.lines().get();
        }
        for (Line line : lines) {
            if (line.row() && line.tip() != null && Ui.in(mouseX, mouseY, leftPos + LEFT_L, topPos + line.y(),
                    leftPos + settingsRight() - VALUE_W - 4, topPos + line.y() + ROW_H)) {
                return line.tip().get();
            }
        }
        Direction tab = tabAt(mouseX, mouseY);
        if (tab != null) return tabTooltip(tab);
        if (Ui.in(mouseX, mouseY, leftPos + W - 130, topPos + 4, leftPos + W - 6, topPos + 26)) {
            return menu.extracting() ? pacingTooltip() : List.of(
                    Component.translatable("gui.flowline.priority", menu.priority()),
                    Component.translatable("gui.flowline.value.priority.desc").withStyle(ChatFormatting.GRAY));
        }
        if (menu.getCarried().isEmpty() && overList(mouseX, mouseY)) {
            Item item = itemAt(mouseX, mouseY);
            if (item != null && item.rule() != null && overChip(mouseX, mouseY, item)) {
                return List.of(chipText(item.rule()),
                        Component.translatable("gui.flowline.chip.toggle").withStyle(ChatFormatting.GRAY));
            }
            if (item != null && item.rule() != null) return ruleTooltip(item.rule());
            if (item != null) {
                return List.of(Component.translatable(menu.firstFreeClient() < 0 ? "gui.flowline.filter.full"
                                : "gui.flowline.filter.add"),
                        Component.translatable("gui.flowline.rule.empty_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        if (Ui.in(mouseX, mouseY, leftPos + PipeMenu.UPGRADE_X - 1, topPos + UPGRADE_TEXT_Y,
                leftPos + RIGHT_R, topPos + UPGRADE_TEXT_Y + 30)) {
            return upgradeLegend();
        }
        if (hoveredSlot instanceof PipeMenu.UpgradeSlot && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            return List.of(Component.translatable("gui.flowline.upgrade_slot"),
                    Component.translatable("gui.flowline.upgrade_slot.desc").withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    private List<Component> ruleTooltip(FilterEntry entry) {
        List<Component> lines = new ArrayList<>();
        if (entry.item().isPresent() && entry.isFluidRule(menu.type)) {
            lines.add(entry.displayFluid().getFluidType().getDescription().copy());
            lines.add(Component.literal(entry.item().get()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (entry.item().isPresent() && entry.isChemicalRule(menu.type)) {
            ResourceLocation chemical = ResourceLocation.tryParse(entry.item().get());
            lines.add((chemical == null ? Component.literal(entry.item().get()) : ChemicalCompat.name(chemical)).copy());
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
                lines.add(Component.literal("  #" + entry.tags().get(i)).withStyle(ChatFormatting.WHITE));
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
                menu.filterCount()).withStyle(ChatFormatting.GRAY));
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

    /** "Label: Value", a grey description line and how to cycle it. */
    private List<Component> describe(String labelKey, String valueKey) {
        return List.of(
                Component.translatable(labelKey, Component.translatable(valueKey).withStyle(ChatFormatting.WHITE)),
                Component.translatable(valueKey + ".desc").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.flowline.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
