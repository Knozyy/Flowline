package com.knozyy.flowline.client;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The filter "library": pick a sample from the inventory, then tick the tags and data components a rule should
 * match. Opened from {@link PipeScreen} while the pipe menu stays open; closing it returns there.
 */
public class RuleEditorScreen extends Screen {
    private static final int W = 300, H = 240;
    private static final int BG = 0xFF1E2229, BG_EDGE = 0xFF0E1014, PANEL = 0xFF262B33, PANEL_EDGE = 0xFF343A45;
    private static final int SLOT = 0xFF14171C, SLOT_EDGE = 0xFF3A404C, TEXT = 0xFFE6E9EF, MUTED = 0xFF8A93A3;
    private static final int ERROR = 0xFFFF6B6B, ROW_HOVER = 0xFF30363F;
    private static final int ROW_H = 11;

    // column bounds, relative to the panel origin
    private static final int BODY_T = 24, BODY_B = 126;
    private static final int LEFT_L = 6, LEFT_R = 92, MID_L = 96, MID_R = 204, RIGHT_L = 208, RIGHT_R = 294;
    private static final int TAG_LIST_T = 50, NBT_LIST_T = 38;
    private static final int INV_Y = 160, HOTBAR_Y = 218, INV_X = (W - 9 * 18) / 2;

    private final PipeScreen parent;
    private final PipeMenu menu;
    private final PipeType type;
    private final int index;
    private final boolean existing;
    private final int accent;

    // rule being edited
    private boolean matchItem;
    private String itemId;
    private final Set<ResourceLocation> selectedTags = new LinkedHashSet<>();
    private boolean allTags;
    private CompoundTag nbt;
    private boolean exact;
    private boolean invert;

    // library state
    private ItemStack sample = ItemStack.EMPTY;
    private List<ResourceLocation> sampleTags = List.of();
    private CompoundTag sampleData = new CompoundTag();
    private String search = "";
    private boolean textMode = false;
    private boolean nbtTextValid = true;
    private int tagScroll = 0;
    private int nbtScroll = 0;
    private final List<TagRow> tagRows = new ArrayList<>();
    private final List<NbtRow> nbtRows = new ArrayList<>();
    @Nullable
    private ResourceLocation hoveredTag;

    private int left, top;
    private EditBox itemBox;
    private EditBox searchBox;
    private MultiLineEditBox nbtText;
    private Button saveButton;
    private Button anyAllButton;
    private IconButton exactButton;

    private record TagRow(ResourceLocation id) {}

    private record NbtRow(String key) {}

    public RuleEditorScreen(PipeScreen parent, PipeMenu menu, int index, @Nullable FilterEntry entry, int accent) {
        super(Component.translatable("gui.flowline.editor.title", index + 1));
        this.parent = parent;
        this.menu = menu;
        this.type = menu.type;
        this.index = index;
        this.existing = entry != null;
        this.accent = accent;
        FilterEntry rule = entry != null ? entry
                : new FilterEntry(Optional.empty(), List.of(), false, Optional.empty(), false, false);
        this.matchItem = rule.item().isPresent();
        this.itemId = rule.item().orElse("");
        for (String tag : rule.tags()) {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id != null) selectedTags.add(id);
        }
        this.allTags = rule.allTags();
        this.nbt = rule.nbt().map(CompoundTag::copy).orElseGet(CompoundTag::new);
        this.exact = rule.exactNbt();
        this.invert = rule.invert();
        if (rule.item().isPresent()) setSample(rule.displayStack(type, menu.registries()), false);
    }

    // ---- setup --------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        // header
        addRenderableWidget(new IconButton(left + 132, top + 4, 14, () -> invert ? "blacklist" : "whitelist", accent,
                b -> invert = !invert));
        IconButton delete = addRenderableWidget(new IconButton(left + 150, top + 4, 14, () -> "clear", accent, b -> {
            send(null);
            onClose();
        }));
        delete.active = existing;
        addRenderableWidget(Button.builder(Component.translatable("gui.flowline.editor.cancel"), b -> onClose())
                .bounds(left + W - 94, top + 4, 40, 14).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.flowline.editor.save"), b -> save())
                .bounds(left + W - 50, top + 4, 44, 14).build());

        // item
        itemBox = addRenderableWidget(new EditBox(font, left + LEFT_L + 3, top + 106, LEFT_R - LEFT_L - 6, 12,
                Component.empty()));
        itemBox.setMaxLength(256);
        itemBox.setHint(Component.literal(type == PipeType.FLUID ? "minecraft:water" : "minecraft:stone")
                .withStyle(ChatFormatting.DARK_GRAY));
        itemBox.setValue(itemId);
        itemBox.setResponder(value -> {
            itemId = value;
            validate();
        });

        // tags
        searchBox = addRenderableWidget(new EditBox(font, left + MID_L + 3, top + 36, MID_R - MID_L - 6, 12,
                Component.empty()));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("gui.flowline.library.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value.trim().toLowerCase(Locale.ROOT);
            tagScroll = 0;
            rebuildTagRows();
        });
        anyAllButton = addRenderableWidget(Button.builder(Component.empty(), b -> allTags = !allTags)
                .bounds(left + MID_R - 30, top + 24, 27, 11).build());

        // data components
        exactButton = addRenderableWidget(new IconButton(left + RIGHT_R - 26, top + 24, 11,
                () -> exact ? "match_components" : "ignore_components", accent, b -> exact = !exact));
        addRenderableWidget(new IconButton(left + RIGHT_R - 14, top + 24, 11, () -> textMode ? "page_prev" : "page_next",
                accent, b -> setTextMode(!textMode)));
        nbtText = addRenderableWidget(new MultiLineEditBox(font, left + RIGHT_L + 2, top + NBT_LIST_T,
                RIGHT_R - RIGHT_L - 4, BODY_B - NBT_LIST_T - 2, Component.literal("{}"), Component.empty()));
        nbtText.setCharacterLimit(4096);
        nbtText.setValueListener(this::onNbtText);
        setTextMode(textMode);

        rebuildTagRows();
        rebuildNbtRows();
        validate();
    }

    private void setTextMode(boolean on) {
        textMode = on;
        nbtText.visible = on;
        if (on) {
            nbtText.setValue(nbt.isEmpty() ? "" : nbt.toString());
            nbtTextValid = true;
        } else {
            rebuildNbtRows();
        }
        validate();
    }

    private void onNbtText(String text) {
        if (!textMode) return;
        try {
            nbt = FilterEntry.parseNbt(text).orElseGet(CompoundTag::new);
            nbtTextValid = true;
        } catch (CommandSyntaxException e) {
            nbtTextValid = false;
        }
        validate();
    }

    // ---- sample -------------------------------------------------------------------------------------------

    private void setSample(ItemStack stack, boolean fromClick) {
        sample = stack.copyWithCount(1);
        sampleTags = FilterEntry.tagsOf(type, sample);
        sampleData = FilterEntry.componentsOf(type, sample, menu.registries());
        String id = FilterEntry.idOf(type, sample);
        if (fromClick && id != null) {
            itemId = id;
            if (itemBox != null) itemBox.setValue(id);
            if (selectedTags.isEmpty()) matchItem = true;
        }
        tagScroll = 0;
        nbtScroll = 0;
        rebuildTagRows();
        rebuildNbtRows();
        validate();
    }

    // ---- rows ---------------------------------------------------------------------------------------------

    private void rebuildTagRows() {
        tagRows.clear();
        Set<ResourceLocation> shown = new LinkedHashSet<>();
        if (search.isEmpty()) {
            shown.addAll(selectedTags);
            shown.addAll(sampleTags);
        } else {
            // library search: every known tag whose id contains the text
            String query = search.startsWith("#") ? search.substring(1) : search;
            selectedTags.stream().filter(id -> id.toString().contains(query)).forEach(shown::add);
            FilterEntry.allTags(type).filter(id -> id.toString().contains(query)).sorted().limit(60)
                    .forEach(shown::add);
        }
        for (ResourceLocation id : shown) tagRows.add(new TagRow(id));
    }

    private void rebuildNbtRows() {
        nbtRows.clear();
        Set<String> keys = new LinkedHashSet<>(nbt.getAllKeys());
        keys.addAll(sampleData.getAllKeys());
        for (String key : keys.stream().sorted().toList()) nbtRows.add(new NbtRow(key));
    }

    private int visibleRows(int listTop) {
        return (BODY_B - listTop - 2) / ROW_H;
    }

    // ---- saving -------------------------------------------------------------------------------------------

    private FilterEntry draft() {
        String id = itemId.trim();
        List<String> tags = selectedTags.stream().map(ResourceLocation::toString).toList();
        return new FilterEntry(matchItem && !id.isEmpty() ? Optional.of(id) : Optional.empty(), tags, allTags,
                nbt.isEmpty() ? Optional.empty() : Optional.of(nbt.copy()), exact, invert);
    }

    @Nullable
    private String problem() {
        if (textMode && !nbtTextValid) return "gui.flowline.editor.error.nbt";
        FilterEntry draft = draft();
        String problem = draft.problem(type);
        if (problem != null) return problem;
        // the client only knows the visible page; the server checks the whole filter again
        int pageStart = index - index % com.knozyy.flowline.pipe.SideConfig.FILTER_PAGE;
        for (int i = 0; i < com.knozyy.flowline.pipe.SideConfig.FILTER_PAGE; i++) {
            FilterEntry other = menu.clientEntry(i);
            if (pageStart + i != index && other != null && other.sameMatch(draft)) return "gui.flowline.editor.error.exists";
        }
        return null;
    }

    private void validate() {
        if (saveButton == null) return;
        String problem = problem();
        saveButton.active = problem == null;
        boolean itemBad = matchItem && problem != null && (problem.endsWith("unknown_item")
                || problem.endsWith("unknown_fluid") || problem.endsWith("syntax"));
        itemBox.setTextColor(itemBad ? 0xFF6B6B : 0xE0E0E0);
        exactButton.active = !nbt.isEmpty();
    }

    private void save() {
        if (problem() != null) return;
        send(draft());
        onClose();
    }

    private void send(@Nullable FilterEntry entry) {
        PacketDistributor.sendToServer(new SetFilterEntryPayload(menu.containerId, index, Optional.ofNullable(entry)));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- input --------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int mx = (int) mouseX - left, my = (int) mouseY - top;

        // "match this item" checkbox
        if (in(mx, my, LEFT_L + 3, 92, LEFT_R - 3, 102)) {
            matchItem = !matchItem;
            validate();
            return true;
        }
        // tag rows
        int tagRow = rowAt(mx, my, MID_L, MID_R, TAG_LIST_T, tagRows.size(), tagScroll);
        if (tagRow >= 0) {
            ResourceLocation id = tagRows.get(tagRow).id();
            if (!selectedTags.remove(id) && selectedTags.size() < FilterEntry.MAX_TAGS) selectedTags.add(id);
            validate();
            return true;
        }
        // component rows
        if (!textMode) {
            int nbtRow = rowAt(mx, my, RIGHT_L, RIGHT_R, NBT_LIST_T, nbtRows.size(), nbtScroll);
            if (nbtRow >= 0) {
                String key = nbtRows.get(nbtRow).key();
                if (nbt.contains(key)) {
                    nbt.remove(key);
                } else if (sampleData.contains(key)) {
                    nbt.put(key, sampleData.get(key).copy());
                }
                validate();
                return true;
            }
        }
        // inventory: pick a sample
        ItemStack stack = inventoryStackAt(mx, my);
        if (!stack.isEmpty()) {
            setSample(stack, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX - left, my = (int) mouseY - top;
        int step = scrollY < 0 ? 1 : -1;
        if (in(mx, my, MID_L, TAG_LIST_T, MID_R, BODY_B)) {
            tagScroll = clampScroll(tagScroll + step, tagRows.size(), visibleRows(TAG_LIST_T));
            return true;
        }
        if (!textMode && in(mx, my, RIGHT_L, NBT_LIST_T, RIGHT_R, BODY_B)) {
            nbtScroll = clampScroll(nbtScroll + step, nbtRows.size(), visibleRows(NBT_LIST_T));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static int clampScroll(int value, int rows, int visible) {
        return Math.max(0, Math.min(value, Math.max(0, rows - visible)));
    }

    private int rowAt(int mx, int my, int l, int r, int listTop, int rows, int scroll) {
        if (mx < l + 2 || mx >= r - 2 || my < listTop || my >= BODY_B - 2) return -1;
        int row = (my - listTop) / ROW_H;
        if (row >= visibleRows(listTop)) return -1;
        int index = row + scroll;
        return index < rows ? index : -1;
    }

    private static boolean in(int mx, int my, int l, int t, int r, int b) {
        return mx >= l && mx < r && my >= t && my < b;
    }

    private ItemStack inventoryStackAt(int mx, int my) {
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int x = slotX(i), y = slotY(i);
            if (in(mx, my, x, y, x + 16, y + 16)) return inventory.getItem(i);
        }
        return ItemStack.EMPTY;
    }

    private static int slotX(int inventoryIndex) {
        return INV_X + (inventoryIndex % 9) * 18;
    }

    private static int slotY(int inventoryIndex) {
        return inventoryIndex < 9 ? HOTBAR_Y : INV_Y + (inventoryIndex / 9 - 1) * 18;
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int l = left, t = top, r = left + W, b = top + H;
        graphics.fill(l + 1, t - 1, r - 1, b + 1, BG_EDGE);
        graphics.fill(l - 1, t + 1, r + 1, b - 1, BG_EDGE);
        graphics.fill(l, t, r, b, BG);
        graphics.fill(l + 1, t, r - 1, t + 2, accent);
        panel(graphics, LEFT_L, LEFT_R);
        panel(graphics, MID_L, MID_R);
        panel(graphics, RIGHT_L, RIGHT_R);
    }

    private void panel(GuiGraphics graphics, int l, int r) {
        graphics.fill(left + l, top + BODY_T, left + r, top + BODY_B, PANEL_EDGE);
        graphics.fill(left + l + 1, top + BODY_T + 1, left + r - 1, top + BODY_B - 1, PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        anyAllButton.setMessage(Component.translatable(allTags ? "gui.flowline.library.all" : "gui.flowline.library.any"));
        anyAllButton.active = selectedTags.size() > 1;
        super.render(graphics, mouseX, mouseY, partialTick);

        int mx = mouseX - left, my = mouseY - top;
        hoveredTag = null;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        renderHeader(graphics);
        renderSampleColumn(graphics, mx, my);
        renderTagColumn(graphics, mx, my);
        renderNbtColumn(graphics, mx, my);
        renderTagPreview(graphics);
        renderInventory(graphics, mx, my);
        graphics.pose().popPose();
        renderTooltips(graphics, mouseX, mouseY, mx, my);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(font, getTitle(), 8, 7, TEXT, false);
        String problem = problem();
        if (problem != null && !draft().isEmpty()) {
            small(graphics, Component.translatable(problem), 8, 17, ERROR);
        } else {
            small(graphics, Component.translatable(invert ? "gui.flowline.editor.block" : "gui.flowline.editor.allow"),
                    8, 17, invert ? ERROR : 0xFF6BD68A);
        }
    }

    private void renderSampleColumn(GuiGraphics graphics, int mx, int my) {
        small(graphics, caption("gui.flowline.library.sample"), LEFT_L + 4, BODY_T + 4, MUTED);
        int sx = (LEFT_L + LEFT_R) / 2 - 17, sy = BODY_T + 14;
        graphics.fill(sx - 1, sy - 1, sx + 35, sy + 35, sample.isEmpty() ? SLOT_EDGE : accent);
        graphics.fill(sx, sy, sx + 34, sy + 34, SLOT);
        if (!sample.isEmpty() && type.filtersFluids()) {
            FluidIcon.draw(graphics, FilterEntry.fluidOf(sample), sx + 1, sy + 1, 32);
        } else if (!sample.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(sx + 1, sy + 1, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.renderItem(sample, 0, 0);
            graphics.pose().popPose();
        } else {
            smallCentered(graphics, Component.translatable("gui.flowline.library.pick"), sx + 17, sy + 9, MUTED, 44);
        }
        Component under = sample.isEmpty() ? Component.translatable("gui.flowline.library.pick_hint")
                : type.filtersFluids() ? FilterEntry.fluidOf(sample).getFluidType().getDescription()
                : sample.getHoverName();
        smallCentered(graphics, under, (LEFT_L + LEFT_R) / 2, sy + 38, sample.isEmpty() ? MUTED : TEXT, 80);

        // "match this item" checkbox
        checkbox(graphics, LEFT_L + 4, 94, matchItem);
        small(graphics, Component.translatable("gui.flowline.library.match_item"), LEFT_L + 13, 94, TEXT);
    }

    private void renderTagColumn(GuiGraphics graphics, int mx, int my) {
        small(graphics, caption("gui.flowline.library.tags"), MID_L + 4, BODY_T + 4, MUTED);
        int visible = visibleRows(TAG_LIST_T);
        if (tagRows.isEmpty()) {
            smallCentered(graphics, Component.translatable(search.isEmpty() ? "gui.flowline.library.no_tags"
                    : "gui.flowline.library.no_results"), (MID_L + MID_R) / 2, TAG_LIST_T + 8, MUTED, 100);
        }
        for (int row = 0; row < visible && row + tagScroll < tagRows.size(); row++) {
            ResourceLocation id = tagRows.get(row + tagScroll).id();
            int y = TAG_LIST_T + row * ROW_H;
            boolean hover = in(mx, my, MID_L + 2, y, MID_R - 2, y + ROW_H);
            if (hover) {
                graphics.fill(MID_L + 2, y, MID_R - 2, y + ROW_H, ROW_HOVER);
                hoveredTag = id;
            }
            boolean checked = selectedTags.contains(id);
            checkbox(graphics, MID_L + 4, y + 2, checked);
            int color = sampleTags.contains(id) || sample.isEmpty() ? TEXT : MUTED;
            small(graphics, Component.literal(fit("#" + id, MID_R - MID_L - 20)), MID_L + 13, y + 2, checked ? accent : color);
        }
        scrollbar(graphics, MID_R - 3, TAG_LIST_T, BODY_B - 2, tagScroll, tagRows.size(), visible);
    }

    private void renderNbtColumn(GuiGraphics graphics, int mx, int my) {
        small(graphics, caption("gui.flowline.library.data"), RIGHT_L + 4, BODY_T + 4, MUTED);
        if (textMode) return;
        int visible = visibleRows(NBT_LIST_T);
        if (nbtRows.isEmpty()) {
            smallCentered(graphics, Component.translatable("gui.flowline.library.no_data"), (RIGHT_L + RIGHT_R) / 2,
                    NBT_LIST_T + 8, MUTED, 80);
        }
        for (int row = 0; row < visible && row + nbtScroll < nbtRows.size(); row++) {
            String key = nbtRows.get(row + nbtScroll).key();
            int y = NBT_LIST_T + row * ROW_H;
            if (in(mx, my, RIGHT_L + 2, y, RIGHT_R - 2, y + ROW_H)) {
                graphics.fill(RIGHT_L + 2, y, RIGHT_R - 2, y + ROW_H, ROW_HOVER);
            }
            boolean checked = nbt.contains(key);
            checkbox(graphics, RIGHT_L + 4, y + 2, checked);
            small(graphics, Component.literal(fit(componentName(key), RIGHT_R - RIGHT_L - 20)), RIGHT_L + 13, y + 2,
                    checked ? accent : TEXT);
        }
        scrollbar(graphics, RIGHT_R - 3, NBT_LIST_T, BODY_B - 2, nbtScroll, nbtRows.size(), visible);
    }

    /** Members of the hovered tag, below the columns. */
    private void renderTagPreview(GuiGraphics graphics) {
        int y = BODY_B + 4;
        if (hoveredTag == null) {
            small(graphics, Component.translatable("gui.flowline.library.preview_hint"), LEFT_L, y + 5, MUTED);
            return;
        }
        int size = FilterEntry.tagSize(type, hoveredTag);
        small(graphics, Component.translatable("gui.flowline.library.members", size), LEFT_L, y + 5, MUTED);
        if (type.filtersFluids()) {
            List<net.minecraft.world.level.material.Fluid> fluids = FilterEntry.tagFluids(hoveredTag, 12);
            for (int i = 0; i < fluids.size(); i++) FluidIcon.draw(graphics, fluids.get(i), 80 + i * 17, y, 16);
            return;
        }
        List<ItemStack> members = FilterEntry.tagMembers(type, hoveredTag, 12);
        for (int i = 0; i < members.size(); i++) graphics.renderItem(members.get(i), 80 + i * 17, y);
    }

    private void renderInventory(GuiGraphics graphics, int mx, int my) {
        small(graphics, Component.translatable("gui.flowline.library.inventory"), INV_X, INV_Y - 9, MUTED);
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int x = slotX(i), y = slotY(i);
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            }
            if (in(mx, my, x, y, x + 16, y + 16)) graphics.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, int mx, int my) {
        ItemStack stack = inventoryStackAt(mx, my);
        if (!stack.isEmpty()) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
            return;
        }
        if (!textMode) {
            int row = rowAt(mx, my, RIGHT_L, RIGHT_R, NBT_LIST_T, nbtRows.size(), nbtScroll);
            if (row >= 0) {
                String key = nbtRows.get(row).key();
                Tag value = nbt.contains(key) ? nbt.get(key) : sampleData.get(key);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(componentName(key)));
                lines.add(Component.literal(key).withStyle(ChatFormatting.DARK_GRAY));
                if (value != null) {
                    String text = value.toString();
                    if (text.length() > 120) text = text.substring(0, 117) + "...";
                    lines.add(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                lines.add(Component.translatable(nbt.contains(key) ? "gui.flowline.library.data_on"
                        : "gui.flowline.library.data_off").withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (anyAllButton.isHovered()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(allTags ? "gui.flowline.library.all" : "gui.flowline.library.any"),
                    Component.translatable(allTags ? "gui.flowline.library.all.desc" : "gui.flowline.library.any.desc")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (exactButton.isHovered()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(exact ? "gui.flowline.editor.exact" : "gui.flowline.editor.contains"),
                    Component.translatable(exact ? "gui.flowline.editor.exact.desc" : "gui.flowline.editor.contains.desc")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (in(mx, my, RIGHT_R - 14, BODY_T, RIGHT_R - 3, BODY_T + 11)) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable(
                    textMode ? "gui.flowline.library.list_mode" : "gui.flowline.library.text_mode")), mouseX, mouseY);
        } else if (in(mx, my, 132, 4, 146, 18)) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(invert ? "gui.flowline.editor.block" : "gui.flowline.editor.allow"),
                    Component.translatable(invert ? "gui.flowline.editor.block.desc" : "gui.flowline.editor.allow.desc")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (in(mx, my, 150, 4, 164, 18)) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable("gui.flowline.editor.delete")),
                    mouseX, mouseY);
        }
    }

    // ---- drawing helpers ----------------------------------------------------------------------------------

    private void checkbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 7, y + 7, checked ? accent : SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + 6, y + 6, checked ? accent : SLOT);
        if (checked) graphics.fill(x + 2, y + 2, x + 5, y + 5, TEXT);
    }

    private void scrollbar(GuiGraphics graphics, int x, int t, int b, int scroll, int rows, int visible) {
        if (rows <= visible) return;
        int track = b - t;
        int thumb = Math.max(6, track * visible / rows);
        int y = t + (track - thumb) * scroll / Math.max(1, rows - visible);
        graphics.fill(x, t, x + 2, b, PANEL_EDGE);
        graphics.fill(x, y, x + 2, y + thumb, accent);
    }

    /** "minecraft:enchantments" -> "Enchantments", "!minecraft:food" -> "- Food" (a removed default). */
    private static String componentName(String key) {
        boolean removed = key.startsWith("!");
        String id = removed ? key.substring(1) : key;
        ResourceLocation location = ResourceLocation.tryParse(id);
        String path = location == null ? id
                : location.getNamespace().equals("minecraft") ? location.getPath() : location.toString();
        String pretty = path.replace('_', ' ');
        if (!pretty.isEmpty()) pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
        return removed ? "- " + pretty : pretty;
    }

    /** Cuts text to fit {@code width} pixels at the small (0.75) scale. */
    private String fit(String text, int width) {
        int max = (int) (width / 0.75f);
        if (font.width(text) <= max) return text;
        return font.plainSubstrByWidth(text, max - font.width("…")) + "…";
    }

    private static Component caption(String key) {
        return Component.literal(Component.translatable(key).getString().toUpperCase(Locale.ROOT));
    }

    private void small(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void smallCentered(GuiGraphics graphics, Component text, int centerX, int y, int color, int width) {
        var lines = font.split(text, (int) (width / 0.75f));
        for (int i = 0; i < lines.size(); i++) {
            float w = font.width(lines.get(i)) * 0.75f;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX - w / 2f, y + i * 8, 0);
            graphics.pose().scale(0.75f, 0.75f, 1f);
            graphics.drawString(font, lines.get(i), 0, 0, color, false);
            graphics.pose().popPose();
        }
    }
}
