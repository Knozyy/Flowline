package com.knozyy.flowline.menu;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.network.FilterPagePayload;
import com.knozyy.flowline.pipe.Distribution;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.knozyy.flowline.network.ModNetwork;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * Configuration screen for one side of a pipe (extracting or inserting). The server owns the {@link SideConfig}; the client only
 * mirrors its scalar values through data slots, its filter through ghost slots and its upgrades through real slots.
 *
 * <p>Slot layout: 0..5 = upgrades, 6..14 = filter (not on energy pipes), then the player inventory. Inserting sides
 * only take Filter upgrades.
 * Filter slots hold the samples themselves: items for item pipes, filled containers for fluid pipes. The nine filter
 * slots show one page of the side's filter; Filter upgrades raise the capacity and add pages.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int BTN_DISTRIBUTION = 1;
    public static final int BTN_REDSTONE = 2;
    public static final int BTN_CLEAR = 4;
    public static final int BTN_PREV_PAGE = 6;
    public static final int BTN_NEXT_PAGE = 7;
    public static final int BTN_DISTRIBUTION_BACK = 8;
    public static final int BTN_REDSTONE_BACK = 9;
    /** Toggles channel {@code id - BTN_CHANNEL} on universal pipes: 0 items, 1 fluids, 2 energy. */
    public static final int BTN_CHANNEL = 10;

    /** Fields set through {@link com.knozyy.flowline.network.SetSideValuePayload}. */
    public static final int FIELD_PRIORITY = 0;
    public static final int FIELD_LIMIT = 1;
    public static final int FIELD_RATE = 2;

    /** Top-left of the 2x3 upgrade grid. */
    public static final int UPGRADE_X = 128;
    public static final int UPGRADE_Y = 44;
    /** Top-left of the 3x3 filter grid. */
    public static final int FILTER_X = 60;
    public static final int FILTER_Y = 44;
    public static final int INVENTORY_Y = 136;
    public static final int HOTBAR_Y = 194;

    private static final int UPGRADES = SideConfig.UPGRADE_SLOTS;

    public final BlockPos pos;
    public final Direction side;
    public final PipeType type;

    /** Null on the client. */
    private final PipeBlockEntity pipe;
    private final SideConfig cfg;

    private final SimpleContainer filterInv = new SimpleContainer(SideConfig.FILTER_PAGE);
    /** Filter page shown in the ghost slots; server side, mirrored to the client through {@link #pageData}. */
    private int page = 0;
    /** Server: the player to send filter pages to, and whether the visible page changed since the last send. */
    @Nullable
    private final ServerPlayer player;
    private boolean pageDirty = true;
    private final HolderLookup.Provider registries;
    /** Client: rules on the visible page, as last sent by the server; used by the rule editor and overlays. */
    private final FilterEntry[] clientEntries = new FilterEntry[SideConfig.FILTER_PAGE];
    private final int ghostCount;
    private final int inventoryStart;
    /** The mode the side had when the menu opened (sent with the open packet); the menu closes if it changes. */
    private final SideMode openedMode;

    private final DataSlot modeData;
    private final DataSlot distributionData;
    private final DataSlot redstoneData;
    private final DataSlot speedCountData;
    private final DataSlot stackCountData;
    private final DataSlot filterCountData;
    private final DataSlot intervalData;
    private final DataSlot startData;
    private final DataSlot minData;
    private final DataSlot multiplierData;
    private final DataSlot sleepingData;
    private final DataSlot pageData;
    private final DataSlot capacityData;
    private final DataSlot priorityData;
    private final DataSlot channelsData;
    private final IntSupplier limitData;
    private final IntSupplier rateData;
    private final IntSupplier itemsData;

    /** Client constructor, fed by the extra data written in {@code PipeBlock}. */
    public PipeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(Direction.class), buf.readEnum(PipeType.class),
                buf.readEnum(SideMode.class), null);
    }

    /** Server constructor. */
    public PipeMenu(int id, Inventory inventory, PipeBlockEntity pipe, Direction side) {
        this(id, inventory, pipe.getBlockPos(), side, pipe.type(), pipe.side(side).mode, pipe);
    }

    private PipeMenu(int id, Inventory inventory, BlockPos pos, Direction side, PipeType type, SideMode mode,
                     PipeBlockEntity pipe) {
        super(ModMenus.PIPE.get(), id);
        this.pos = pos;
        this.side = side;
        this.type = type;
        this.pipe = pipe;
        this.cfg = pipe == null ? null : pipe.side(side);
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.registries = inventory.player.level().registryAccess();
        this.ghostCount = type.hasFilter() ? SideConfig.FILTER_PAGE : 0;
        this.openedMode = mode;

        // The client mirrors the whole upgrade container so slot indices match the server's.
        Container upgrades = pipe != null ? pipe.upgrades() : new SimpleContainer(6 * UPGRADES);
        for (int i = 0; i < UPGRADES; i++) {
            addSlot(new UpgradeSlot(upgrades, PipeBlockEntity.upgradeSlot(side, i),
                    UPGRADE_X + (i % 2) * 18, UPGRADE_Y + (i / 2) * 18));
        }
        for (int i = 0; i < ghostCount; i++) {
            addSlot(new GhostSlot(filterInv, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        inventoryStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }

        modeData = track(() -> cfg.mode.ordinal());
        distributionData = track(() -> cfg.distribution.ordinal());
        redstoneData = track(() -> cfg.redstone.ordinal());
        speedCountData = track(() -> cfg.speedCount);
        stackCountData = track(() -> cfg.stackCount);
        filterCountData = track(() -> cfg.filterCount);
        intervalData = track(() -> cfg.interval < 0 ? Pacing.start(cfg.speedCount) : cfg.interval);
        startData = track(() -> Pacing.start(cfg.speedCount));
        minData = track(Pacing::min);
        multiplierData = track(() -> Pacing.stackMultiplier(cfg.stackCount));
        sleepingData = track(() -> cfg.sleeping ? 1 : 0);
        pageData = track(() -> page);
        capacityData = track(() -> cfg.filterCapacity());
        priorityData = track(() -> cfg.priority);
        channelsData = track(() -> cfg.channels);
        limitData = trackInt(() -> cfg.limit);
        rateData = trackInt(() -> cfg.rate);
        itemsData = trackInt(() -> Pacing.itemsPerOperation(cfg.stackCount));

        if (cfg != null) loadPage();
    }

    private DataSlot track(IntSupplier server) {
        if (cfg == null) return addDataSlot(DataSlot.standalone());
        return addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return server.getAsInt();
            }

            @Override
            public void set(int value) {}
        });
    }

    /** Data slots only carry 16 bits: an int travels as two of them. */
    private IntSupplier trackInt(IntSupplier server) {
        DataSlot low = track(() -> server.getAsInt() & 0xFFFF);
        DataSlot high = track(() -> server.getAsInt() >>> 16);
        return () -> (high.get() & 0xFFFF) << 16 | low.get() & 0xFFFF;
    }

    public HolderLookup.Provider registries() {
        return registries;
    }

    /** Energy pipes have no filter. */
    public boolean hasFilter() {
        return ghostCount > 0;
    }

    // ---- state for the screen -----------------------------------------------------------------------------

    public SideMode mode() {
        return SideMode.values()[modeData.get()];
    }

    public Distribution distribution() {
        return Distribution.values()[distributionData.get()];
    }

    public RedstoneMode redstone() {
        return RedstoneMode.values()[redstoneData.get()];
    }

    public int speedCount() {
        return speedCountData.get();
    }

    public int stackCount() {
        return stackCountData.get();
    }

    public int filterCount() {
        return filterCountData.get();
    }

    /** Current ticks between operations. */
    public int interval() {
        return intervalData.get();
    }

    public int startInterval() {
        return startData.get();
    }

    public int minInterval() {
        return minData.get();
    }

    public int multiplier() {
        return multiplierData.get();
    }

    public boolean sleeping() {
        return sleepingData.get() != 0;
    }

    public int page() {
        return pageData.get();
    }

    /** Usable filter entries on this side. */
    public int capacity() {
        return capacityData.get();
    }

    /** Known from the open packet, so the screen can lay itself out before any data slot arrives. */
    public boolean extracting() {
        return openedMode == SideMode.EXTRACT;
    }

    public int priority() {
        return (short) priorityData.get();
    }

    public int limit() {
        return limitData.getAsInt();
    }

    public int rate() {
        return rateData.getAsInt();
    }

    /** Items one operation of this side may move. */
    public int itemsPerOperation() {
        return itemsData.getAsInt();
    }

    public int channels() {
        return channelsData.get();
    }

    public int pageCount() {
        return Math.max(1, (capacity() + SideConfig.FILTER_PAGE - 1) / SideConfig.FILTER_PAGE);
    }

    // ---- buttons ------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cfg == null) return false;
        switch (id) {
            case BTN_DISTRIBUTION -> cfg.distribution = cfg.distribution.next();
            case BTN_DISTRIBUTION_BACK -> cfg.distribution = cfg.distribution.previous();
            case BTN_REDSTONE -> cfg.redstone = cfg.redstone.next();
            case BTN_REDSTONE_BACK -> cfg.redstone = cfg.redstone.previous();
            case BTN_CHANNEL, BTN_CHANNEL + 1, BTN_CHANNEL + 2 -> {
                if (!type.hasChannels()) return false;
                cfg.channels ^= 1 << (id - BTN_CHANNEL);
                cfg.wake();
            }
            case BTN_CLEAR -> {
                if (!hasFilter()) return false;
                cfg.clearFilter();
                loadPage();
            }
            case BTN_PREV_PAGE, BTN_NEXT_PAGE -> {
                int target = page + (id == BTN_NEXT_PAGE ? 1 : -1);
                if (!hasFilter() || target < 0 || target >= pageCount()) return false;
                page = target;
                loadPage();
                return true;
            }
            default -> {
                return false;
            }
        }
        pipe.setChanged();
        return true;
    }

    /** Server: a number typed or scrolled in the screen. */
    public void setValue(int field, int value) {
        if (cfg == null) return;
        switch (field) {
            case FIELD_PRIORITY -> cfg.priority = Math.max(-SideConfig.MAX_PRIORITY, Math.min(SideConfig.MAX_PRIORITY, value));
            case FIELD_LIMIT -> cfg.limit = Math.max(0, Math.min(SideConfig.MAX_AMOUNT, value));
            case FIELD_RATE -> cfg.rate = Math.max(0, Math.min(SideConfig.MAX_AMOUNT, value));
            default -> {
                return;
            }
        }
        cfg.wake();
        pipe.setChanged();
    }

    // ---- ghost filter slots -------------------------------------------------------------------------------

    private boolean isGhost(int slotId) {
        return slotId >= UPGRADES && slotId < UPGRADES + ghostCount;
    }

    /**
     * Ghost slots: click with a stack to turn it into a rule. Clicks with an empty hand are handled by the screen
     * (open the rule library, or shift-click to remove the rule).
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isGhost(slotId)) {
            if (clickType == ClickType.PICKUP) clickGhost((GhostSlot) slots.get(slotId), getCarried(), button);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void clickGhost(GhostSlot slot, ItemStack carried, int button) {
        int index = slot.filterIndex();
        if (cfg == null || carried.isEmpty() || index >= capacity()) return;   // server decides; the page syncs back
        FilterEntry entry = FilterEntry.fromStack(type, carried, registries);
        if (entry != null) setEntry(index, entry);
    }

    /** Server: replace one rule (null removes it), then refresh the page for the client. */
    public void setEntry(int index, @Nullable FilterEntry entry) {
        if (cfg == null || !hasFilter() || index < 0 || index >= cfg.filterCapacity()) return;
        if (entry != null && entry.problem(type) != null) return;
        if (entry != null) {
            for (int i = 0; i < cfg.filterCapacity(); i++) {
                FilterEntry other = cfg.getEntry(i);
                if (i != index && other != null && other.sameMatch(entry)) {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.flowline.rule_exists", i + 1), true);
                    }
                    return;
                }
            }
        }
        cfg.setEntry(index, entry);
        pipe.setChanged();
        loadPage();
    }

    /** Client: the rules on the visible page, from {@link com.knozyy.flowline.network.FilterPagePayload}. */
    public void receivePage(int page, List<Optional<FilterEntry>> entries) {
        for (int i = 0; i < clientEntries.length; i++) {
            clientEntries[i] = i < entries.size() ? entries.get(i).orElse(null) : null;
        }
    }

    /** Client: the rule shown in ghost slot {@code slotIndex} of the visible page. */
    @Nullable
    public FilterEntry clientEntry(int slotIndex) {
        return slotIndex >= 0 && slotIndex < clientEntries.length ? clientEntries[slotIndex] : null;
    }

    /** Server: show the current page in the ghost slots and queue the rules for the client. */
    private void loadPage() {
        for (int i = 0; i < filterInv.getContainerSize(); i++) {
            FilterEntry entry = cfg.getEntry(page * SideConfig.FILTER_PAGE + i);
            // fluid rules are drawn by the screen with the fluid's own texture, not as a bucket
            filterInv.setItem(i, entry == null || entry.isFluidRule(type) ? ItemStack.EMPTY
                    : entry.displayStack(type, registries));
        }
        pageDirty = true;
    }

    /** Keeps the page valid when a Filter upgrade is taken out, and sends page rules after changes. */
    @Override
    public void broadcastChanges() {
        if (cfg != null && page >= pageCount()) {
            page = pageCount() - 1;
            loadPage();
        }
        super.broadcastChanges();
        if (cfg != null && pageDirty && player != null) {
            pageDirty = false;
            List<Optional<FilterEntry>> entries = new ArrayList<>();
            for (int i = 0; i < SideConfig.FILTER_PAGE; i++) {
                entries.add(Optional.ofNullable(cfg.getEntry(page * SideConfig.FILTER_PAGE + i)));
            }
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FilterPagePayload(containerId, page, entries));
        }
    }

    // ---- menu plumbing ------------------------------------------------------------------------------------

    /** Shift-click moves upgrades between the player inventory and the upgrade slots. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < UPGRADES) {
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) return ItemStack.EMPTY;
        } else if (index >= inventoryStart && slots.get(0).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 0, UPGRADES, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pipe == null) return true;
        return !pipe.isRemoved() && cfg.mode == openedMode
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    /** Holds one upgrade item. */
    public class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (pipe != null) return pipe.accepts(side, stack);
            return stack.getItem() instanceof UpgradeItem
                    && (extracting() || UpgradeItem.typeOf(stack) == com.knozyy.flowline.item.UpgradeType.FILTER);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * A slot that only displays a filter entry: nothing can be put in or taken out by the vanilla logic. Positions
     * past the side's capacity (a partial last page) are hidden.
     */
    public class GhostSlot extends Slot {
        GhostSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        /** Position of this slot's rule in the whole filter. */
        public int filterIndex() {
            return page() * SideConfig.FILTER_PAGE + getContainerSlot();
        }

        @Override
        public boolean isActive() {
            return filterIndex() < capacity();
        }
    }
}
