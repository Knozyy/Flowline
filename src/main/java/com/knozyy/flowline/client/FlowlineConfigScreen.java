package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-game editor for Flowline's configs (Mods > Flowline > Config). The per-player client config can be edited at
 * any time. The gameplay config is a server config, so it is only editable while a single player world (or a LAN
 * world this client hosts) is open. Each field is checked with the config's own validator; Done writes the files,
 * which apply without a restart.
 */
public class FlowlineConfigScreen extends Screen {
    private static final int ROW_H = 22, HEADER_H = 16, TOP = 32, ROW_W = 310, BOX_W = 120;
    private static final int TEXT = 0xFFFFFF, HEADER = 0xFFD37F, MUTED = 0xA0A0A0, ERROR = 0xFF5555;

    private record Section(String key, ForgeConfigSpec spec, boolean client, List<ForgeConfigSpec.ConfigValue<?>> values) {}

    /** Sections and values in file order, client settings first. */
    private static final List<Section> SECTIONS = List.of(
            new Section("client", FlowlineConfig.Client.SPEC, true, List.of(FlowlineConfig.Client.RENDER_ITEMS,
                    FlowlineConfig.Client.MAX_TRAVELLING, FlowlineConfig.Client.TICKS_PER_PIPE)),
            new Section("amounts", FlowlineConfig.SPEC, false, List.of(FlowlineConfig.ITEMS_PER_OPERATION,
                    FlowlineConfig.FLUID_PER_OPERATION, FlowlineConfig.ENERGY_PER_TICK,
                    FlowlineConfig.CHEMICAL_PER_OPERATION, FlowlineConfig.STACK_MULTIPLIERS,
                    FlowlineConfig.ITEM_STACK_MULTIPLIERS, FlowlineConfig.FLUID_STACK_MULTIPLIERS,
                    FlowlineConfig.CHEMICAL_STACK_MULTIPLIERS)),
            new Section("pacing", FlowlineConfig.SPEC, false, List.of(FlowlineConfig.START_INTERVAL,
                    FlowlineConfig.MIN_INTERVAL, FlowlineConfig.MAX_IDLE_INTERVAL, FlowlineConfig.SPEED_REDUCTION,
                    FlowlineConfig.ACCELERATION_STEP, FlowlineConfig.IDLE_BACKOFF_FACTOR)),
            new Section("filter", FlowlineConfig.SPEC, false, List.of(FlowlineConfig.BASE_FILTER_SLOTS,
                    FlowlineConfig.FILTER_SLOTS_PER_UPGRADE)),
            new Section("network", FlowlineConfig.SPEC, false, List.of(FlowlineConfig.MAX_NETWORK_SIZE)),
            new Section("animations", FlowlineConfig.SPEC, false, List.of(FlowlineConfig.SEND_ANIMATIONS)));

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FlowlineConfigScreen(parent)));
    }

    private final Screen parent;
    /** Text typed so far ("true"/"false" for switches), kept across resizes (which rebuild the widgets). */
    private final Map<ForgeConfigSpec.ConfigValue<?>, String> drafts = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private boolean serverEditable;
    /** Content y of the "open a world to edit" note shown instead of the server sections, or -1. */
    private int noteY = -1;
    private int scroll, contentHeight;
    private Button doneButton;

    private record Header(String key, int y) {}

    private static final class Row {
        final ForgeConfigSpec.ConfigValue<?> value;
        final ForgeConfigSpec.ValueSpec spec;
        final String key;
        final int y;
        AbstractWidget widget;
        /** The entered value if valid, else null. */
        @Nullable Object parsed;

        Row(Section section, ForgeConfigSpec.ConfigValue<?> value, int y) {
            this.value = value;
            this.spec = section.spec().getSpec().get(value.getPath());
            this.key = value.getPath().get(value.getPath().size() - 1);
            this.y = y;
        }
    }

    public FlowlineConfigScreen(Screen parent) {
        super(Component.translatable("flowline.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        headers.clear();
        noteY = -1;
        serverEditable = FlowlineConfig.isLoaded() && minecraft != null && minecraft.hasSingleplayerServer();

        int y = 0;
        for (Section section : SECTIONS) {
            if (!section.client() && !serverEditable) {
                if (noteY < 0) {
                    noteY = y;
                    y += 10 * font.split(Component.translatable("flowline.configuration.world_only"), ROW_W).size() + 8;
                }
                continue;
            }
            headers.add(new Header(section.key(), y));
            y += HEADER_H;
            for (ForgeConfigSpec.ConfigValue<?> value : section.values()) {
                Row row = new Row(section, value, y);
                drafts.computeIfAbsent(value, v -> format(v.get()));
                row.widget = addRenderableWidget(value instanceof ForgeConfigSpec.BooleanValue ? toggle(row) : textBox(row));
                check(row);
                rows.add(row);
                y += ROW_H;
            }
            y += 4;
        }
        contentHeight = y;

        int buttonY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("flowline.configuration.reset"), b -> resetToDefaults())
                .bounds(width / 2 - 155, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 - 50, buttonY, 100, 20).build());
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(width / 2 + 55, buttonY, 100, 20).build());
        updateDone();
        layoutRows();
    }

    private EditBox textBox(Row row) {
        EditBox box = new EditBox(font, width / 2 + ROW_W / 2 - BOX_W, 0, BOX_W, 16, label(row.key));
        box.setMaxLength(256);
        box.setValue(drafts.get(row.value));
        box.setResponder(text -> {
            drafts.put(row.value, text);
            check(row);
            updateDone();
        });
        return box;
    }

    private Button toggle(Row row) {
        return Button.builder(onOff(row), b -> {
            drafts.put(row.value, Boolean.toString(!Boolean.parseBoolean(drafts.get(row.value))));
            check(row);
            b.setMessage(onOff(row));
        }).bounds(width / 2 + ROW_W / 2 - BOX_W, 0, BOX_W, 20).build();
    }

    private Component onOff(Row row) {
        return Boolean.parseBoolean(drafts.get(row.value)) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private int viewBottom() {
        return height - 34;
    }

    private void layoutRows() {
        scroll = Math.max(0, Math.min(scroll, contentHeight - (viewBottom() - TOP)));
        for (Row row : rows) {
            int y = TOP + row.y - scroll;
            row.widget.setY(y + (ROW_H - row.widget.getHeight()) / 2);
            row.widget.visible = y >= TOP && y + ROW_H <= viewBottom();
        }
    }

    private void check(Row row) {
        row.parsed = parse(row, drafts.get(row.value));
        if (row.widget instanceof EditBox box) box.setTextColor(row.parsed == null ? ERROR : 0xE0E0E0);
    }

    private void updateDone() {
        if (doneButton != null) doneButton.active = rows.stream().allMatch(row -> row.parsed != null);
    }

    /** Parses {@code text} for the row's type and runs the config's validator on it; null if either fails. */
    @Nullable
    private static Object parse(Row row, String text) {
        try {
            Object parsed;
            if (row.value instanceof ForgeConfigSpec.BooleanValue) {
                parsed = Boolean.parseBoolean(text);
            } else if (row.value instanceof ForgeConfigSpec.IntValue) {
                parsed = Integer.parseInt(text.trim());
            } else if (row.value instanceof ForgeConfigSpec.DoubleValue) {
                parsed = Double.parseDouble(text.trim());
            } else {
                List<Integer> list = new ArrayList<>();
                for (String part : text.split(",")) {
                    if (!part.isBlank()) list.add(Integer.parseInt(part.trim()));
                }
                parsed = list;
            }
            return row.spec.test(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(Object value) {
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return String.valueOf(value);
    }

    private void resetToDefaults() {
        for (Row row : rows) {
            String text = format(row.spec.getDefault());
            drafts.put(row.value, text);
            if (row.widget instanceof EditBox box) box.setValue(text);
            check(row);
            if (row.widget instanceof Button button) button.setMessage(onOff(row));
        }
        updateDone();
    }

    private void save() {
        for (Row row : rows) set(row.value, row.parsed);
        FlowlineConfig.Client.SPEC.save();
        if (serverEditable) FlowlineConfig.SPEC.save();
        onClose();
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(ForgeConfigSpec.ConfigValue<T> value, Object parsed) {
        value.set((T) parsed);
    }

    private static Component label(String key) {
        return Component.translatableWithFallback("flowline.configuration." + key, key);
    }

    /** Translated description (or the config comment), then the allowed range and the default. */
    private Component tooltip(Row row) {
        String key = "flowline.configuration." + row.key + ".tooltip";
        String comment = row.spec.getComment() == null ? "" : row.spec.getComment();
        MutableComponent text = label(row.key).copy().append("\n");
        if (Language.getInstance().has(key)) {
            text.append(Component.translatable(key));
            // the comment's "Range: ..." line is added by the config builder; keep it under the translation
            comment.lines().filter(line -> line.startsWith("Range:")).forEach(line -> text.append("\n" + line));
        } else {
            text.append(comment);
        }
        Object fallback = row.spec.getDefault();
        Component shown = fallback instanceof Boolean on ? (on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)
                : Component.literal(format(fallback));
        text.append("\n").append(Component.translatable("flowline.configuration.default", shown));
        if (row.parsed == null) {
            text.append("\n").append(Component.translatable("flowline.configuration.invalid").withStyle(s -> s.withColor(ERROR)));
        }
        return text;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) (delta * ROW_H);
        layoutRows();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, TEXT);

        int left = width / 2 - ROW_W / 2;
        for (Header header : headers) {
            int y = TOP + header.y() - scroll;
            if (y >= TOP && y + HEADER_H <= viewBottom()) graphics.drawString(font, label(header.key()), left, y + 5, HEADER);
        }
        if (noteY >= 0) {
            int y = TOP + noteY - scroll + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("flowline.configuration.world_only"), ROW_W)) {
                if (y >= TOP && y + 10 <= viewBottom()) graphics.drawString(font, line, left, y, MUTED);
                y += 10;
            }
        }
        Row hovered = null;
        for (Row row : rows) {
            if (!row.widget.visible) continue;
            int y = TOP + row.y - scroll;
            graphics.drawString(font, label(row.key), left + 8, y + 7, row.parsed == null ? ERROR : TEXT);
            if (mouseX >= left && mouseX < left + ROW_W && mouseY >= y && mouseY < y + ROW_H) hovered = row;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) graphics.renderTooltip(font, font.split(tooltip(hovered), 250), mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
