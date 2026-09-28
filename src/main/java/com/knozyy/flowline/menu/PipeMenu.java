package com.knozyy.flowline.menu;

import com.knozyy.flowline.item.UpgradeItem;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

/**
 * Configuration screen for one extracting side of a pipe. The server owns the {@link SideConfig}; the client only
 * mirrors its scalar values through data slots, its filter through ghost slots and its upgrades through real slots.
 *
 * <p>Slot layout: 0..5 = upgrades, 6..14 = filter (not on energy pipes), then the player inventory.
 * Filter slots hold the samples themselves: items for item pipes, filled containers for fluid pipes. The nine filter
 * slots show one page of the side's filter; Filter upgrades raise the capacity and add pages.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int BTN_DISTRIBUTION = 1;
    public static final int BTN_REDSTONE = 2;
    public static final int BTN_WHITELIST = 3;
    public static final int BTN_CLEAR = 4;
    public static final int BTN_MATCH = 5;
    public static final int BTN_PREV_PAGE = 6;
    public static final int BTN_NEXT_PAGE = 7;

    /** Top-left of the 2x3 upgrade grid. */
    public static final int UPGRADE_X = 128;
    public static final int UPGRADE_Y = 44;
    /** Top-left of the 3x3 filter grid. */
    public static final int FILTER_X = 60;
    public static final int FILTER_Y = 44;
    public static final int INVENTORY_Y = 114;
    public static final int HOTBAR_Y = 172;

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
    private final int ghostCount;
    private final int inventoryStart;

    private final DataSlot modeData;
    private final DataSlot distributionData;
    private final DataSlot redstoneData;
    private final DataSlot whitelistData;
    private final DataSlot matchData;
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

    /** Client constructor, fed by the extra data written in {@code PipeBlock}. */
    public PipeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(Direction.class), buf.readEnum(PipeType.class), null);
    }

    /** Server constructor. */
    public PipeMenu(int id, Inventory inventory, PipeBlockEntity pipe, Direction side) {
        this(id, inventory, pipe.getBlockPos(), side, pipe.type(), pipe);
    }

    private PipeMenu(int id, Inventory inventory, BlockPos pos, Direction side, PipeType type, PipeBlockEntity pipe) {
        super(ModMenus.PIPE.get(), id);
        this.pos = pos;
        this.side = side;
        this.type = type;
        this.pipe = pipe;
        this.cfg = pipe == null ? null : pipe.side(side);
        this.ghostCount = type == PipeType.ENERGY ? 0 : SideConfig.FILTER_PAGE;

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
        whitelistData = track(() -> cfg.whitelist ? 1 : 0);
        matchData = track(() -> cfg.matchComponents ? 1 : 0);
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

    public boolean whitelist() {
        return whitelistData.get() != 0;
    }

    public boolean matchComponents() {
        return matchData.get() != 0;
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

    public int pageCount() {
        return Math.max(1, (capacity() + SideConfig.FILTER_PAGE - 1) / SideConfig.FILTER_PAGE);
    }

    // ---- buttons ------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cfg == null) return false;
        switch (id) {
            case BTN_DISTRIBUTION -> cfg.distribution = cfg.distribution.next();
            case BTN_REDSTONE -> cfg.redstone = cfg.redstone.next();
            case BTN_WHITELIST -> {
                if (!hasFilter()) return false;
                cfg.whitelist = !cfg.whitelist;
            }
            case BTN_MATCH -> {
                if (!hasFilter()) return false;
                cfg.matchComponents = !cfg.matchComponents;
            }
            case BTN_CLEAR -> {
                if (!hasFilter()) return false;
                cfg.filter.clear();
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

    // ---- ghost filter slots -------------------------------------------------------------------------------

    private boolean isGhost(int slotId) {
        return slotId >= UPGRADES && slotId < UPGRADES + ghostCount;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isGhost(slotId)) {
            if (clickType != ClickType.QUICK_CRAFT) clickGhost(slots.get(slotId), getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void clickGhost(Slot slot, ItemStack carried) {
        int index = page() * SideConfig.FILTER_PAGE + slot.getContainerSlot();
        if (index >= capacity()) return;
        ItemStack value = ItemStack.EMPTY;
        if (!carried.isEmpty()) {
            if (!SideConfig.isValidSample(type, carried) || filterContains(carried)) return;
            value = carried.copyWithCount(1);
        }
        slot.set(value);
        if (cfg != null) {
            cfg.setSample(index, value.copy());
            pipe.setChanged();
        }
    }

    /** Duplicates are rejected across all pages on the server; the client can only check the visible page. */
    private boolean filterContains(ItemStack stack) {
        if (cfg != null) {
            int end = Math.min(cfg.filter.size(), cfg.filterCapacity());
            for (int i = 0; i < end; i++) {
                if (ItemStack.isSameItemSameComponents(cfg.filter.get(i), stack)) return true;
            }
            return false;
        }
        for (int i = 0; i < filterInv.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(filterInv.getItem(i), stack)) return true;
        }
        return false;
    }

    /** Server: copy the current page of the filter into the ghost slots. */
    private void loadPage() {
        for (int i = 0; i < filterInv.getContainerSize(); i++) {
            filterInv.setItem(i, cfg.getSample(page * SideConfig.FILTER_PAGE + i).copy());
        }
    }

    /** Keeps the page valid when a Filter upgrade is taken out while the screen is open. */
    @Override
    public void broadcastChanges() {
        if (cfg != null && page >= pageCount()) {
            page = pageCount() - 1;
            loadPage();
        }
        super.broadcastChanges();
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
        } else if (index >= inventoryStart && stack.getItem() instanceof UpgradeItem) {
            if (!moveItemStackTo(stack, 0, UPGRADES, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pipe == null) return true;
        return !pipe.isRemoved() && cfg.mode == SideMode.EXTRACT
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    /** Holds one upgrade item. */
    public static class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem;
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
    private class GhostSlot extends Slot {
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

        @Override
        public boolean isActive() {
            return page() * SideConfig.FILTER_PAGE + getContainerSlot() < capacity();
        }
    }
}
