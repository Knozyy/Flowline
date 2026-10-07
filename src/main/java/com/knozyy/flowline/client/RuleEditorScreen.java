package com.knozyy.flowline.client;

import com.knozyy.flowline.client.ui.FlatButton;
import com.knozyy.flowline.client.ui.RuleText;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.client.ui.Ui;
import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.filter.CompiledFilter;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.util.Stacks;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The filter "library": pick a sample from the inventory, then tick the tags and data a rule should match, and add
 * mod, name, durability and amount conditions below. A summary line says what the rule does and whether the sample
 * matches it. Opened from {@link PipeScreen} while the pipe menu stays open; closing it returns there.
 * <p>
 * It is a container screen over a slotless {@link Blank} menu only so that recipe viewers (JEI, EMI) show their
 * ingredient list beside it, which they do for container screens. The pipe's real menu stays open underneath and is
 * what rules are sent to; no slot of it is drawn or clicked here.
 */
public class RuleEditorScreen extends AbstractContainerScreen<RuleEditorScreen.Blank> {
    private static final int W = 300, H = 234;
    private static final int ROW_H = 10;

    // section bounds, relative to the window origin
    private static final int BODY_T = 22, BODY_B = 106;
    private static final int LEFT_L = 6, LEFT_R = 80, MID_L = 84, MID_R = 210, RIGHT_L = 214, RIGHT_R = 294;
    private static final int TAG_LIST_T = 48, TAG_LIST_B = 88, PREVIEW_Y = 91;
    private static final int NBT_LIST_T = 36, NBT_LIST_B = 91;
    /** Row under the sections: mod, name pattern, durability range, amount. */
    private static final int EXTRA_T = 109, EXTRA_B = 123;
    private static final int MOD_L = 6, MOD_R = 70, NAME_L = 74, NAME_R = 160, DUR_L = 164, DUR_R = 228;
    private static final int AMT_L = 232, AMT_R = 294;
    private static final int SUMMARY_T = 127, SUMMARY_B = 150;
    private static final int INV_Y = 155, HOTBAR_Y = 213, INV_X = (W - 9 * 18) / 2;
    private static final int PREVIEW_ICONS = 8, PREVIEW_STEP = 13;

    private final PipeScreen parent;
    /** The pipe's open menu (hides the container screen's own {@code menu}, which is the {@link Blank}). */
    private final PipeMenu menu;
    /** Registry kind of the rule being edited: FLUID for fluid rules, ITEM otherwise. */
    private PipeType type;
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
    private boolean fluidRule;
    /** Chemical pipes: rules know an id, a mod and a name only, so tags, data and durability are hidden. */
    private final boolean chemicalRule;
    private FlatButton textToggle;
    private String mod = "";
    private String name = "";
    /** Per-rule regulator, 0 = off (see {@link FilterEntry#amount}). */
    private int amount = 0;
    private int minDurability = 0, maxDurability = 100;

    // library state
    private ItemStack sample = ItemStack.EMPTY;
    private List<ResourceLocation> sampleTags = List.of();
    private CompoundTag sampleData = new CompoundTag();
    private String search = "";
    private boolean textMode = false;
    private boolean nbtTextValid = true;
    private int tagScroll = 0;
    private int nbtScroll = 0;
    private final List<ResourceLocation> tagRows = new ArrayList<>();
    private final List<String> nbtRows = new ArrayList<>();
    /** The last tag hovered, previewed while no tag is ticked, so the mouse can still reach its items. */
    @Nullable
    private ResourceLocation lastHoveredTag;
    /** What the preview shows, and which tags it was built from. */
    private String previewKey = "";
    private List<ItemStack> previewMembers = List.of();
    private int previewTotal = 0;

    private int left, top;
    private EditBox itemBox;
    private EditBox searchBox;
    private EditBox modBox;
    private EditBox nameBox;
    private EditBox amountBox;
    private EditBox minBox;
    private EditBox maxBox;
    private MultiLineEditBox nbtText;
    private FlatButton saveButton;
    private FlatButton anyAllButton;
    private FlatButton exactButton;
    private final List<EditBox> fields = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    /** False while {@link #init} builds the widgets: field responders fire on their first value, before the rest exists. */
    private boolean ready = false;

    private record Tip(AbstractWidget widget, Supplier<List<Component>> lines) {}

    /** A menu without slots, so the container screen machinery has nothing to draw, click or sync. */
    public static final class Blank extends AbstractContainerMenu {
        private Blank(int containerId) {
            super(null, containerId);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    public RuleEditorScreen(PipeScreen parent, PipeMenu menu, int index, @Nullable FilterEntry entry, int accent) {
        super(new Blank(menu.containerId), Minecraft.getInstance().player.getInventory(),
                Component.translatable("gui.flowline.editor.title", index + 1));
        this.imageWidth = W;
        this.imageHeight = H;
        this.parent = parent;
        this.menu = menu;
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
        this.fluidRule = rule.isFluidRule(menu.type);
        this.chemicalRule = menu.type == PipeType.CHEMICAL;
        this.type = chemicalRule ? PipeType.CHEMICAL : fluidRule ? PipeType.FLUID : PipeType.ITEM;
        this.mod = rule.mod().orElse("");
        this.name = rule.name().orElse("");
        this.amount = rule.amount();
        this.minDurability = rule.minDurability();
        this.maxDurability = rule.maxDurability();
        if (rule.item().isPresent()) setSample(rule.displayStack(menu.type, menu.registries()), false);
    }

    // ---- setup --------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        ready = false;
        left = leftPos;
        top = topPos;
        fields.clear();
        tips.clear();

        // header
        FlatButton allow = addRenderableWidget(new FlatButton(left + 112, top + 4, 32, 13,
                () -> Component.translatable("gui.flowline.chip.allow"), accent, b -> setInvert(false))
                .style(() -> invert ? FlatButton.Style.PLAIN : FlatButton.Style.SELECTED));
        FlatButton block = addRenderableWidget(new FlatButton(left + 144, top + 4, 40, 13,
                () -> Component.translatable("gui.flowline.chip.block"), accent, b -> setInvert(true))
                .style(() -> invert ? FlatButton.Style.SELECTED : FlatButton.Style.PLAIN));
        tips.add(new Tip(allow, () -> List.of(Component.translatable("gui.flowline.editor.allow"),
                Component.translatable("gui.flowline.editor.allow.desc").withStyle(ChatFormatting.GRAY))));
        tips.add(new Tip(block, () -> List.of(Component.translatable("gui.flowline.editor.block"),
                Component.translatable("gui.flowline.editor.block.desc").withStyle(ChatFormatting.GRAY))));
        IconButton delete = addRenderableWidget(new IconButton(left + 189, top + 4, 13, () -> "clear", accent, b -> {
            send(null);
            onClose();
        }));
        delete.visible = existing;
        tips.add(new Tip(delete, () -> List.of(Component.translatable("gui.flowline.editor.delete"))));
        addRenderableWidget(new FlatButton(left + 206, top + 4, 42, 13,
                () -> Component.translatable("gui.flowline.editor.cancel"), accent, b -> onClose()));
        saveButton = addRenderableWidget(new FlatButton(left + 252, top + 4, 42, 13,
                () -> Component.translatable("gui.flowline.editor.save"), accent, b -> save())
                .style(FlatButton.Style.PRIMARY));

        // sample
        if (menu.type == PipeType.UNIVERSAL) {
            FlatButton kind = addRenderableWidget(new FlatButton(left + LEFT_R - 33, top + BODY_T + 2, 30, 11,
                    () -> Component.translatable(fluidRule ? "gui.flowline.editor.kind_fluid_short"
                            : "gui.flowline.editor.kind_item_short"), accent, b -> setFluidRule(!fluidRule)));
            tips.add(new Tip(kind, () -> List.of(
                    Component.translatable(fluidRule ? "gui.flowline.rule.kind_fluid" : "gui.flowline.rule.kind_item"),
                    Component.translatable("gui.flowline.editor.kind.desc").withStyle(ChatFormatting.GRAY))));
        }
        itemBox = field(LEFT_L + 3, 92, LEFT_R - LEFT_L - 6, 256, "", value -> {
            itemId = value;
            validate();
        });
        itemBox.setValue(itemId);
        updateItemHint();

        // tags
        anyAllButton = addRenderableWidget(new FlatButton(left + MID_R - 52, top + BODY_T + 2, 48, 11,
                () -> Component.translatable(allTags ? "gui.flowline.library.all_short" : "gui.flowline.library.any_short"),
                accent, b -> {
                    allTags = !allTags;
                    validate();
                }));
        tips.add(new Tip(anyAllButton, () -> List.of(
                Component.translatable(allTags ? "gui.flowline.library.all_short" : "gui.flowline.library.any_short"),
                Component.translatable(allTags ? "gui.flowline.library.all.desc" : "gui.flowline.library.any.desc")
                        .withStyle(ChatFormatting.GRAY))));
        searchBox = field(MID_L + 3, 34, MID_R - MID_L - 6, 128, Component.translatable("gui.flowline.library.search")
                .getString(), value -> {
            search = value.trim().toLowerCase(Locale.ROOT);
            tagScroll = 0;
            rebuildTagRows();
        });
        searchBox.setValue(search);

        // data
        exactButton = addRenderableWidget(new FlatButton(left + RIGHT_R - 41, top + BODY_T + 2, 38, 11,
                () -> Component.translatable(exact ? "gui.flowline.editor.exact_short" : "gui.flowline.editor.contains_short"),
                accent, b -> {
                    exact = !exact;
                    validate();
                }));
        tips.add(new Tip(exactButton, () -> List.of(
                Component.translatable(exact ? "gui.flowline.editor.exact" : "gui.flowline.editor.contains"),
                Component.translatable(exact ? "gui.flowline.editor.exact.desc" : "gui.flowline.editor.contains.desc")
                        .withStyle(ChatFormatting.GRAY))));
        textToggle = addRenderableWidget(new FlatButton(left + RIGHT_L + 3, top + NBT_LIST_B + 2,
                RIGHT_R - RIGHT_L - 6, 11, () -> Component.translatable(textMode ? "gui.flowline.library.list_short"
                : "gui.flowline.library.text_short"), accent, b -> setTextMode(!textMode)));
        tips.add(new Tip(textToggle, () -> List.of(Component.translatable(
                textMode ? "gui.flowline.library.list_mode" : "gui.flowline.library.text_mode"))));
        nbtText = addRenderableWidget(new MultiLineEditBox(font, left + RIGHT_L + 2, top + NBT_LIST_T,
                RIGHT_R - RIGHT_L - 4, NBT_LIST_B - NBT_LIST_T - 1, Component.literal("{}"), Component.empty()));
        nbtText.setCharacterLimit(4096);
        nbtText.setValueListener(this::onNbtText);
        setTextMode(textMode);

        // extra conditions
        modBox = field(MOD_L, EXTRA_T, MOD_R - MOD_L, 64, "@mod", value -> {
            mod = value.trim().startsWith("@") ? value.trim().substring(1) : value.trim();
            validate();
        });
        modBox.setValue(mod);
        nameBox = field(NAME_L, EXTRA_T, NAME_R - NAME_L, FilterEntry.MAX_TEXT,
                Component.translatable("gui.flowline.library.name_hint").getString(), value -> {
                    name = value;
                    validate();
                });
        nameBox.setValue(name);
        minBox = percentBox(DUR_L, minDurability, value -> minDurability = value);
        maxBox = percentBox(DUR_L + 32, maxDurability, value -> maxDurability = value);
        amountBox = field(AMT_L, EXTRA_T, AMT_R - AMT_L, 9,
                Component.translatable("gui.flowline.library.amount_hint").getString(), text -> {
                    amount = text.isEmpty() ? 0 : Integer.parseInt(text);
                    validate();
                });
        amountBox.setFilter(text -> text.matches("\\d{0,9}"));
        amountBox.setValue(amount == 0 ? "" : Integer.toString(amount));
        tips.add(new Tip(modBox, () -> List.of(Component.translatable("gui.flowline.library.mod"),
                Component.translatable("gui.flowline.library.mod.desc").withStyle(ChatFormatting.GRAY))));
        tips.add(new Tip(nameBox, () -> List.of(Component.translatable("gui.flowline.library.name"),
                Component.translatable("gui.flowline.library.name.desc").withStyle(ChatFormatting.GRAY))));
        Supplier<List<Component>> durabilityTip = () -> List.of(Component.translatable("gui.flowline.library.durability"),
                Component.translatable("gui.flowline.library.durability.desc").withStyle(ChatFormatting.GRAY));
        tips.add(new Tip(minBox, durabilityTip));
        tips.add(new Tip(maxBox, durabilityTip));
        tips.add(new Tip(amountBox, () -> List.of(Component.translatable("gui.flowline.library.amount"),
                Component.translatable(menu.extracting() ? "gui.flowline.library.amount.keep"
                        : "gui.flowline.library.amount.max").withStyle(ChatFormatting.GRAY))));

        rebuildTagRows();
        rebuildNbtRows();
        updateKindWidgets();
        ready = true;
        validate();
    }

    /** A borderless text field drawn inside a well spanning {@code x..x+width} from {@code y} (11 high). */
    private EditBox field(int x, int y, int width, int maxLength, String hint, java.util.function.Consumer<String> responder) {
        EditBox box = addRenderableWidget(new EditBox(font, left + x + 3, top + y + 2, width - 6, 10, Component.empty()));
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setTextColor(Theme.TEXT);
        if (!hint.isEmpty()) box.setHint(hint(hint));
        box.setResponder(responder);
        fields.add(box);
        return box;
    }

    private static Component hint(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(Theme.MUTED & 0xFFFFFF).withItalic(true));
    }

    private EditBox percentBox(int x, int value, IntConsumer setter) {
        EditBox box = field(x, EXTRA_T, 24, 3, "", text -> {
            setter.accept(text.isEmpty() ? 0 : Math.min(100, Integer.parseInt(text)));
            validate();
        });
        box.setFilter(text -> text.matches("\\d{0,3}"));
        box.setValue(Integer.toString(value));
        return box;
    }

    private void setInvert(boolean value) {
        invert = value;
        validate();
    }

    private void updateItemHint() {
        itemBox.setHint(hint(chemicalRule ? "mekanism:hydrogen" : fluidRule ? "minecraft:water" : "minecraft:stone"));
    }

    /** Universal pipes: switch the rule between items and fluids. The sample and item-specific parts are reset. */
    private void setFluidRule(boolean fluid) {
        fluidRule = fluid;
        type = fluid ? PipeType.FLUID : PipeType.ITEM;
        selectedTags.clear();
        nbt = new CompoundTag();
        itemId = "";
        itemBox.setValue("");
        sample = ItemStack.EMPTY;
        sampleTags = List.of();
        sampleData = new CompoundTag();
        lastHoveredTag = null;
        previewKey = "";
        previewMembers = List.of();
        previewTotal = 0;
        if (fluid) {
            minDurability = 0;
            maxDurability = 100;
            minBox.setValue("0");
            maxBox.setValue("100");
        }
        updateItemHint();
        rebuildTagRows();
        rebuildNbtRows();
        updateKindWidgets();
        validate();
    }

    private void updateKindWidgets() {
        minBox.visible = !fluidRule && !chemicalRule;
        maxBox.visible = !fluidRule && !chemicalRule;
        if (chemicalRule) {
            amountBox.visible = false;
            searchBox.visible = false;
            anyAllButton.visible = false;
            exactButton.visible = false;
            textToggle.visible = false;
            nbtText.visible = false;
        }
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
        sample = Stacks.withCount(stack, 1);
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

    // ---- recipe viewer drag and drop -----------------------------------------------------------------------

    /**
     * Where an item or fluid dragged from JEI or EMI can be dropped: the sample section (becomes the sample and the
     * rule's item), the tag section (becomes the sample so its tags are listed to tick) and the mod box (its mod).
     */
    public List<GhostTargets.Slot> dropTargets(ItemStack item, FluidStack fluid) {
        List<GhostTargets.Slot> targets = new ArrayList<>();
        if (modBox == null) return targets;
        FluidStack dragged = fluid.isEmpty() ? FluidUtil.getFluidContained(item).orElse(FluidStack.EMPTY) : fluid;
        // fluid rules take their sample as a filled container
        ItemStack stack = !fluidRule ? item
                : !fluid.isEmpty() ? FluidUtil.getFilledBucket(fluid)
                : dragged.isEmpty() ? ItemStack.EMPTY : item;
        if (!stack.isEmpty()) {
            targets.add(new GhostTargets.Slot(left + LEFT_L, top + BODY_T, LEFT_R - LEFT_L, BODY_B - BODY_T,
                    () -> setSample(stack, true)));
            targets.add(new GhostTargets.Slot(left + MID_L, top + BODY_T, MID_R - MID_L, BODY_B - BODY_T, () -> {
                searchBox.setValue("");
                setSample(stack, false);
            }));
        }
        ResourceLocation id = chemicalRule ? ChemicalCompat.idIn(item)
                : fluidRule ? (dragged.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(dragged.getFluid()))
                : item.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(item.getItem());
        if (id != null) {
            targets.add(new GhostTargets.Slot(left + MOD_L, top + EXTRA_T, MOD_R - MOD_L, EXTRA_B - EXTRA_T,
                    () -> modBox.setValue(id.getNamespace())));
        }
        return targets;
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
        tagRows.addAll(shown);
    }

    private void rebuildNbtRows() {
        nbtRows.clear();
        Set<String> keys = new LinkedHashSet<>(nbt.getAllKeys());
        keys.addAll(sampleData.getAllKeys());
        nbtRows.addAll(keys.stream().sorted().toList());
    }

    /**
     * The preview follows the hovered tag; otherwise it shows what the ticked tags select (all of them in common
     * when "All" is on), and only without ticked tags the last hovered one.
     */
    private void updatePreview(@Nullable ResourceLocation hovered) {
        if (hovered != null) lastHoveredTag = hovered;
        List<ResourceLocation> source = hovered != null ? List.of(hovered)
                : !selectedTags.isEmpty() ? List.copyOf(selectedTags)
                : lastHoveredTag != null ? List.of(lastHoveredTag) : List.of();
        boolean intersect = hovered == null && allTags && source.size() > 1;
        String key = source + "|" + intersect;
        if (key.equals(previewKey)) return;
        previewKey = key;
        java.util.Map<net.minecraft.world.item.Item, ItemStack> members = new java.util.LinkedHashMap<>();
        for (int i = 0; i < source.size(); i++) {
            java.util.Map<net.minecraft.world.item.Item, ItemStack> ofTag = new java.util.LinkedHashMap<>();
            for (ItemStack stack : FilterEntry.tagMembers(type, source.get(i), 512)) ofTag.putIfAbsent(stack.getItem(), stack);
            if (intersect && i > 0) {
                members.keySet().retainAll(ofTag.keySet());
            } else {
                ofTag.forEach(members::putIfAbsent);
            }
        }
        previewTotal = members.size();
        previewMembers = members.values().stream().limit(PREVIEW_ICONS).toList();
    }

    // ---- saving -------------------------------------------------------------------------------------------

    private FilterEntry draft() {
        String id = itemId.trim();
        List<String> tags = selectedTags.stream().map(ResourceLocation::toString).toList();
        return new FilterEntry(matchItem && !id.isEmpty() ? Optional.of(id) : Optional.empty(), tags, allTags,
                nbt.isEmpty() ? Optional.empty() : Optional.of(nbt.copy()), exact, invert,
                mod.isEmpty() ? Optional.empty() : Optional.of(mod), name.isEmpty() ? Optional.empty() : Optional.of(name),
                fluidRule ? 0 : minDurability, fluidRule ? 100 : maxDurability,
                fluidRule && menu.type == PipeType.UNIVERSAL, amount);
    }

    @Nullable
    private String problem() {
        if (textMode && !nbtTextValid) return "gui.flowline.editor.error.nbt";
        FilterEntry draft = draft();
        String problem = draft.problem(menu.type);
        if (problem != null) return problem;
        List<FilterEntry> rules = menu.clientFilter();
        for (int i = 0; i < Math.min(rules.size(), menu.capacity()); i++) {
            FilterEntry other = rules.get(i);
            if (i != index && other != null && other.sameMatch(draft)) return "gui.flowline.editor.error.exists";
        }
        return null;
    }

    private void validate() {
        if (!ready) return;
        String problem = problem();
        saveButton.active = problem == null;
        boolean itemBad = matchItem && problem != null && (problem.endsWith("unknown_item")
                || problem.endsWith("unknown_fluid") || problem.endsWith("unknown_chemical") || problem.endsWith("syntax"));
        itemBox.setTextColor(itemBad ? 0xFF6B6B : Theme.TEXT);
        if (modBox != null) {
            modBox.setTextColor(problem != null && problem.endsWith("unknown_mod") ? 0xFF6B6B : Theme.TEXT);
            nameBox.setTextColor(problem != null && problem.endsWith("regex") ? 0xFF6B6B : Theme.TEXT);
            boolean durBad = problem != null && problem.endsWith("durability");
            minBox.setTextColor(durBad ? 0xFF6B6B : Theme.TEXT);
            maxBox.setTextColor(durBad ? 0xFF6B6B : Theme.TEXT);
        }
        exactButton.active = !nbt.isEmpty();
    }

    private void save() {
        if (problem() != null) return;
        send(draft());
        onClose();
    }

    private void send(@Nullable FilterEntry entry) {
        ModNetwork.sendToServer(new SetFilterEntryPayload(menu.containerId, index, Optional.ofNullable(entry)));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- live match -----------------------------------------------------------------------------------------

    /** Whether the sample passes {@code rule} on its own, as an Allow rule. */
    private boolean matches(FilterEntry rule) {
        CompiledFilter filter = CompiledFilter.compile(List.of(rule.withInvert(false)), menu.type);
        if (chemicalRule) {
            ResourceLocation chemical = ChemicalCompat.idIn(sample);
            return chemical != null
                    && filter.allowsChemical(chemical, () -> ChemicalCompat.name(chemical).getString());
        }
        if (rule.isFluidRule(menu.type)) {
            FluidStack fluid = FluidUtil.getFluidContained(sample).orElse(FluidStack.EMPTY);
            return !fluid.isEmpty() && filter.allowsFluid(fluid);
        }
        return filter.allowsItem(sample);
    }

    /** The first condition of {@code draft} the sample fails, as a translation key. */
    private String failingCondition(FilterEntry draft) {
        boolean fluid = draft.fluid();
        if (draft.item().isPresent() && !matches(FilterEntry.ofItem(draft.item().get()).withFluid(fluid))) {
            return "gui.flowline.editor.match.item";
        }
        if (!draft.tags().isEmpty()
                && !matches(FilterEntry.ofTags(draft.allTags(), draft.tags().toArray(String[]::new)).withFluid(fluid))) {
            return "gui.flowline.editor.match.tags";
        }
        if (draft.mod().isPresent() && !matches(FilterEntry.ofMod(draft.mod().get()).withFluid(fluid))) {
            return "gui.flowline.editor.match.mod";
        }
        if (draft.name().isPresent() && !matches(FilterEntry.ofName(draft.name().get()).withFluid(fluid))) {
            return "gui.flowline.editor.match.name";
        }
        if (draft.hasDurability() && !draft.isFluidRule(menu.type)
                && !matches(FilterEntry.ofDurability(draft.minDurability(), draft.maxDurability()))) {
            return "gui.flowline.editor.match.durability";
        }
        if (draft.nbt().isPresent() && !matches(new FilterEntry(Optional.empty(), List.of(), false, draft.nbt(),
                draft.exactNbt(), false).withFluid(fluid))) {
            return "gui.flowline.editor.match.data";
        }
        return "gui.flowline.editor.match.other";
    }

    /** "Allows: ..." / "Blocks: ...", or what is wrong with the rule. */
    private Component summary(FilterEntry draft, @Nullable String problem) {
        if (draft.isEmpty()) return Component.translatable("gui.flowline.editor.error.empty");
        if (problem != null) return Component.translatable(problem);
        return Component.translatable(invert ? "gui.flowline.editor.summary.block" : "gui.flowline.editor.summary.allow",
                RuleText.conditions(draft, menu.type, menu.registries()));
    }

    @Nullable
    private Component matchLine(FilterEntry draft, @Nullable String problem) {
        if (sample.isEmpty()) return Component.translatable("gui.flowline.editor.match.no_sample");
        if (problem != null || draft.isEmpty()) return null;
        if (matches(draft)) return Component.translatable("gui.flowline.editor.match.yes");
        return Component.translatable("gui.flowline.editor.match.no", Component.translatable(failingCondition(draft)));
    }

    // ---- input --------------------------------------------------------------------------------------------

    // The container screen's own input handling is about slots (and would swallow every click, or close the
    // screen on the inventory key while typing), so widgets get the events directly, like on a plain screen.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (GuiEventListener child : children()) {
            // vanilla's MultiLineEditBox takes left clicks even while hidden, which would cover the data rows
            if (child instanceof AbstractWidget widget && !widget.visible) continue;
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocused(child);
                if (button == 0) setDragging(true);
                return true;
            }
        }
        setFocused(null);
        int mx = (int) mouseX - left, my = (int) mouseY - top;

        // "match this item" checkbox
        if (Ui.in(mx, my, LEFT_L + 3, 80, LEFT_R - 3, 91)) {
            matchItem = !matchItem;
            validate();
            return true;
        }
        int tagRow = rowAt(mx, my, MID_L, MID_R, TAG_LIST_T, TAG_LIST_B, tagRows.size(), tagScroll);
        if (tagRow >= 0) {
            ResourceLocation id = tagRows.get(tagRow);
            if (!selectedTags.remove(id) && selectedTags.size() < FilterEntry.MAX_TAGS) selectedTags.add(id);
            validate();
            return true;
        }
        if (!textMode) {
            int nbtRow = rowAt(mx, my, RIGHT_L, RIGHT_R, NBT_LIST_T, NBT_LIST_B, nbtRows.size(), nbtScroll);
            if (nbtRow >= 0) {
                String key = nbtRows.get(nbtRow);
                if (nbt.contains(key)) {
                    nbt.remove(key);
                } else if (sampleData.contains(key)) {
                    nbt.put(key, sampleData.get(key).copy());
                }
                validate();
                return true;
            }
        }
        ItemStack member = previewAt(mx, my);
        if (!member.isEmpty()) {
            setSample(member, true);
            return true;
        }
        ItemStack stack = inventoryStackAt(mx, my);
        if (!stack.isEmpty()) {
            setSample(stack, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        setDragging(false);
        return getChildAt(mouseX, mouseY).filter(child -> child.mouseReleased(mouseX, mouseY, button)).isPresent();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return getFocused() != null && isDragging() && button == 0
                && getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Typing into a text field must not close the editor (inventory key) or reach the hotbar keys. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB
                && (focused instanceof EditBox box && box.isFocused()
                || focused instanceof MultiLineEditBox text && text.isFocused())) {
            focused.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** No slots here; nothing may reach the pipe's menu as a slot click. */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType type) {}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int mx = (int) mouseX - left, my = (int) mouseY - top;
        int step = scrollY < 0 ? 1 : -1;
        if (Ui.in(mx, my, MID_L, TAG_LIST_T, MID_R, TAG_LIST_B)) {
            tagScroll = clampScroll(tagScroll + step, tagRows.size(), visibleRows(TAG_LIST_T, TAG_LIST_B));
            return true;
        }
        if (!textMode && Ui.in(mx, my, RIGHT_L, NBT_LIST_T, RIGHT_R, NBT_LIST_B)) {
            nbtScroll = clampScroll(nbtScroll + step, nbtRows.size(), visibleRows(NBT_LIST_T, NBT_LIST_B));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private static int visibleRows(int listTop, int listBottom) {
        return (listBottom - listTop) / ROW_H;
    }

    private static int clampScroll(int value, int rows, int visible) {
        return Math.max(0, Math.min(value, Math.max(0, rows - visible)));
    }

    private static int rowAt(int mx, int my, int l, int r, int listTop, int listBottom, int rows, int scroll) {
        if (mx < l + 2 || mx >= r - 2 || my < listTop || my >= listBottom) return -1;
        int row = (my - listTop) / ROW_H;
        if (row >= visibleRows(listTop, listBottom)) return -1;
        int index = row + scroll;
        return index < rows ? index : -1;
    }

    private ItemStack previewAt(int mx, int my) {
        for (int i = 0; i < previewMembers.size(); i++) {
            int x = MID_L + 4 + i * PREVIEW_STEP;
            if (Ui.in(mx, my, x, PREVIEW_Y, x + 12, PREVIEW_Y + 12)) return previewMembers.get(i);
        }
        return ItemStack.EMPTY;
    }

    private ItemStack inventoryStackAt(int mx, int my) {
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int x = slotX(i), y = slotY(i);
            if (Ui.in(mx, my, x, y, x + 16, y + 16)) return inventory.getItem(i);
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    /** The container screen's title and "Inventory" labels; the header draws its own. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);
        Ui.window(g, left, top, left + W, top + H);
        g.fill(left + 6, top + 19, left + W - 6, top + 20, Theme.LINE);
        Ui.panel(g, left + LEFT_L, top + BODY_T, left + LEFT_R, top + BODY_B);
        Ui.panel(g, left + MID_L, top + BODY_T, left + MID_R, top + BODY_B);
        Ui.panel(g, left + RIGHT_L, top + BODY_T, left + RIGHT_R, top + BODY_B);
        for (EditBox box : fields) {
            if (box.visible) {
                Ui.well(g, box.getX() - 3, box.getY() - 2, box.getX() + box.getWidth() + 3, box.getY() + box.getHeight());
            }
        }
        g.fill(left + 6, top + SUMMARY_T, left + W - 6, top + SUMMARY_B, Theme.LINE);
        g.fill(left + 7, top + SUMMARY_T + 1, left + W - 7, top + SUMMARY_B - 1, Theme.SLOT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        anyAllButton.active = selectedTags.size() > 1;
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int mx = mouseX - left, my = mouseY - top;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        Ui.text(g, font, getTitle(), 8, 7, Theme.TEXT);
        renderSampleSection(g);
        renderTagSection(g, mx, my);
        renderNbtSection(g, mx, my);
        renderExtraRow(g);
        renderSummary(g);
        renderInventory(g, mx, my);
        g.pose().popPose();
        renderTooltips(g, mouseX, mouseY, mx, my);
    }

    private static Component chemicalName(ItemStack stack) {
        ResourceLocation id = ChemicalCompat.idIn(stack);
        return id == null ? stack.getHoverName() : ChemicalCompat.name(id);
    }

    private void renderSampleSection(GuiGraphics g) {
        Ui.text(g, font, Component.translatable("gui.flowline.library.sample"), LEFT_L + 4, BODY_T + 4, Theme.MUTED);
        int sx = (LEFT_L + LEFT_R) / 2 - 16, sy = 36;
        g.fill(sx - 1, sy - 1, sx + 33, sy + 33, sample.isEmpty() ? Theme.LINE : accent);
        g.fill(sx, sy, sx + 32, sy + 32, Theme.SLOT);
        if (!sample.isEmpty() && type.filtersFluids()) {
            FluidIcon.draw(g, FilterEntry.fluidOf(sample), sx, sy, 32);
        } else if (!sample.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(sample, 0, 0);
            g.pose().popPose();
        } else {
            Ui.textCentered(g, font, Component.literal("?"), sx + 16, sy + 12, Theme.MUTED);
        }
        Component under = sample.isEmpty() ? Component.translatable("gui.flowline.library.pick")
                : chemicalRule ? chemicalName(sample)
                : type.filtersFluids() ? FilterEntry.fluidOf(sample).getFluidType().getDescription()
                : sample.getHoverName();
        String shown = Ui.fit(font, under.getString(), LEFT_R - LEFT_L - 6);
        Ui.text(g, font, shown, (LEFT_L + LEFT_R) / 2 - font.width(shown) / 2, 70, sample.isEmpty() ? Theme.MUTED : Theme.TEXT);
        Ui.checkbox(g, LEFT_L + 4, 81, matchItem, accent);
        Ui.text(g, font, Ui.fit(font, Component.translatable("gui.flowline.library.match_short").getString(),
                LEFT_R - LEFT_L - 20), LEFT_L + 16, 82, Theme.TEXT);
    }

    private void renderTagSection(GuiGraphics g, int mx, int my) {
        Ui.text(g, font, Component.translatable("gui.flowline.library.tags"), MID_L + 4, BODY_T + 4, Theme.MUTED);
        if (chemicalRule) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("gui.flowline.editor.chemical_hint"), MID_R - MID_L - 10);
            for (int i = 0; i < Math.min(8, lines.size()); i++) {
                g.drawString(font, lines.get(i), MID_L + 5, TAG_LIST_T + 1 + i * 10, Theme.MUTED, false);
            }
            return;
        }
        int visible = visibleRows(TAG_LIST_T, TAG_LIST_B);
        if (tagRows.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable(
                    search.isEmpty() ? "gui.flowline.library.no_tags" : "gui.flowline.library.no_results"),
                    MID_R - MID_L - 10);
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                g.drawString(font, lines.get(i), MID_L + 5, TAG_LIST_T + 1 + i * 10, Theme.MUTED, false);
            }
        }
        ResourceLocation hovered = null;
        for (int row = 0; row < visible && row + tagScroll < tagRows.size(); row++) {
            ResourceLocation id = tagRows.get(row + tagScroll);
            int y = TAG_LIST_T + row * ROW_H;
            if (Ui.in(mx, my, MID_L + 2, y, MID_R - 2, y + ROW_H)) {
                g.fill(MID_L + 2, y, MID_R - 2, y + ROW_H, Theme.HOVER);
                hovered = id;
            }
            boolean checked = selectedTags.contains(id);
            Ui.checkbox(g, MID_L + 4, y, checked, accent);
            String count = Integer.toString(FilterEntry.tagSize(type, id));
            int countX = MID_R - 6 - font.width(count);
            Ui.text(g, font, count, countX, y + 1, Theme.MUTED);
            int color = sampleTags.contains(id) || sample.isEmpty() ? Theme.TEXT : Theme.MUTED;
            Ui.text(g, font, Ui.fit(font, RuleText.tag(id.toString()), countX - MID_L - 20), MID_L + 16, y + 1, color);
        }
        Ui.scrollbar(g, MID_R - 3, TAG_LIST_T, TAG_LIST_B, tagScroll, tagRows.size(), visible, accent);

        updatePreview(hovered);
        g.fill(MID_L + 3, PREVIEW_Y - 2, MID_R - 3, PREVIEW_Y - 1, Theme.LINE);
        if (previewMembers.isEmpty()) {
            Ui.text(g, font, Ui.fit(font, Component.translatable("gui.flowline.library.preview_short").getString(),
                    MID_R - MID_L - 8), MID_L + 4, PREVIEW_Y + 3, Theme.MUTED);
            return;
        }
        for (int i = 0; i < previewMembers.size(); i++) {
            int x = MID_L + 4 + i * PREVIEW_STEP;
            if (Ui.in(mx, my, x, PREVIEW_Y, x + 12, PREVIEW_Y + 12)) g.fill(x - 1, PREVIEW_Y - 1, x + 13, PREVIEW_Y + 13, Theme.HOVER);
            g.pose().pushPose();
            g.pose().translate(x, PREVIEW_Y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(previewMembers.get(i), 0, 0);
            g.pose().popPose();
        }
        if (previewTotal > previewMembers.size()) {
            Ui.textRight(g, font, Component.literal("+" + (previewTotal - previewMembers.size())), MID_R - 5, PREVIEW_Y + 3,
                    Theme.MUTED);
        }
    }

    private void renderNbtSection(GuiGraphics g, int mx, int my) {
        Ui.text(g, font, Component.translatable("gui.flowline.library.data_short"), RIGHT_L + 4, BODY_T + 4, Theme.MUTED);
        if (textMode || chemicalRule) return;
        int visible = visibleRows(NBT_LIST_T, NBT_LIST_B);
        if (nbtRows.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable(
                    "gui.flowline.library.no_data"), RIGHT_R - RIGHT_L - 10);
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                g.drawString(font, lines.get(i), RIGHT_L + 5, NBT_LIST_T + 1 + i * 10, Theme.MUTED, false);
            }
        }
        for (int row = 0; row < visible && row + nbtScroll < nbtRows.size(); row++) {
            String key = nbtRows.get(row + nbtScroll);
            int y = NBT_LIST_T + row * ROW_H;
            if (Ui.in(mx, my, RIGHT_L + 2, y, RIGHT_R - 2, y + ROW_H)) {
                g.fill(RIGHT_L + 2, y, RIGHT_R - 2, y + ROW_H, Theme.HOVER);
            }
            Ui.checkbox(g, RIGHT_L + 4, y, nbt.contains(key), accent);
            Ui.text(g, font, Ui.fit(font, componentName(key), RIGHT_R - RIGHT_L - 24), RIGHT_L + 16, y + 1, Theme.TEXT);
        }
        Ui.scrollbar(g, RIGHT_R - 3, NBT_LIST_T, NBT_LIST_B, nbtScroll, nbtRows.size(), visible, accent);
    }

    private void renderExtraRow(GuiGraphics g) {
        if (!fluidRule && !chemicalRule) {
            Ui.text(g, font, "–", DUR_L + 26, EXTRA_T + 2, Theme.MUTED);
            Ui.text(g, font, "%", DUR_L + 58, EXTRA_T + 2, Theme.MUTED);
        }
    }

    private void renderSummary(GuiGraphics g) {
        FilterEntry draft = draft();
        String problem = problem();
        int width = W - 20;
        Ui.text(g, font, Ui.fit(font, summary(draft, problem).getString(), width), 10, SUMMARY_T + 3,
                problem != null && !draft.isEmpty() ? 0xFFFF8A80 : Theme.TEXT);
        Component match = matchLine(draft, problem);
        if (match != null) Ui.text(g, font, Ui.fit(font, match.getString(), width), 10, SUMMARY_T + 13, Theme.MUTED);
    }

    private void renderInventory(GuiGraphics g, int mx, int my) {
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int x = slotX(i), y = slotY(i);
            g.fill(x - 1, y - 1, x + 17, y + 17, Theme.LINE);
            g.fill(x, y, x + 16, y + 16, Theme.SLOT);
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x, y);
                g.renderItemDecorations(font, stack, x, y);
            }
            if (Ui.in(mx, my, x, y, x + 16, y + 16)) g.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int mx, int my) {
        ItemStack stack = inventoryStackAt(mx, my);
        if (stack.isEmpty()) stack = previewAt(mx, my);
        if (!stack.isEmpty()) {
            g.renderTooltip(font, stack, mouseX, mouseY);
            return;
        }
        for (Tip tip : tips) {
            if (tip.widget().visible && tip.widget().isHovered()) {
                g.renderComponentTooltip(font, tip.lines().get(), mouseX, mouseY);
                return;
            }
        }
        if (!textMode) {
            int row = rowAt(mx, my, RIGHT_L, RIGHT_R, NBT_LIST_T, NBT_LIST_B, nbtRows.size(), nbtScroll);
            if (row >= 0) {
                g.renderComponentTooltip(font, nbtTooltip(nbtRows.get(row)), mouseX, mouseY);
                return;
            }
        }
        int tagRow = rowAt(mx, my, MID_L, MID_R, TAG_LIST_T, TAG_LIST_B, tagRows.size(), tagScroll);
        if (tagRow >= 0) {
            ResourceLocation id = tagRows.get(tagRow);
            g.renderComponentTooltip(font, List.of(Component.literal("#" + id),
                    Component.translatable("gui.flowline.library.members", FilterEntry.tagSize(type, id))
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (Ui.in(mx, my, 6, SUMMARY_T, W - 6, SUMMARY_B)) {
            FilterEntry draft = draft();
            String problem = problem();
            List<Component> lines = new ArrayList<>();
            lines.add(summary(draft, problem));
            Component match = matchLine(draft, problem);
            if (match != null) lines.add(match.copy().withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private List<Component> nbtTooltip(String key) {
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
        return lines;
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
}
