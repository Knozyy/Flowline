package com.knozyy.flowline.menu;

import com.knozyy.flowline.pipe.Distribution;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SpeedTier;
import com.knozyy.flowline.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 * Configuration screen for one side of a pipe. The server owns the {@link SideConfig}; the client only mirrors
 * its scalar values through data slots and its filter through ghost slots.
 *
 * <p>Filter slots hold the samples themselves: items for item pipes, filled containers for fluid pipes.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int BTN_DISTRIBUTION = 1;
    public static final int BTN_REDSTONE = 2;
    public static final int BTN_WHITELIST = 3;
    public static final int BTN_CLEAR = 4;
    public static final int BTN_MATCH = 5;

    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 98;
    public static final int INVENTORY_Y = 130;
    public static final int HOTBAR_Y = 188;

    public final BlockPos pos;
    public final Direction side;
    public final PipeType type;
    /** Whether the side has an upgrade, which unlocks the filter. Fixed while the screen is open. */
    public final boolean upgraded;

    /** Null on the client. */
    private final PipeBlockEntity pipe;
    private final SideConfig cfg;

    private final SimpleContainer filterInv = new SimpleContainer(SideConfig.MAX_FILTER);
    private final int ghostCount;

    private final DataSlot modeData;
    private final DataSlot distributionData;
    private final DataSlot redstoneData;
    private final DataSlot whitelistData;
    private final DataSlot speedData;
    private final DataSlot matchData;

    /** Client constructor, fed by the extra data written in {@code PipeBlock}. */
    public PipeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(Direction.class), buf.readEnum(PipeType.class),
                buf.readBoolean(), null);
    }

    /** Server constructor. */
    public PipeMenu(int id, Inventory inventory, PipeBlockEntity pipe, Direction side) {
        this(id, inventory, pipe.getBlockPos(), side, pipe.type(), pipe.side(side).speed.isUpgraded(), pipe);
    }

    private PipeMenu(int id, Inventory inventory, BlockPos pos, Direction side, PipeType type, boolean upgraded,
                     PipeBlockEntity pipe) {
        super(ModMenus.PIPE.get(), id);
        this.pos = pos;
        this.side = side;
        this.type = type;
        this.upgraded = upgraded;
        this.pipe = pipe;
        this.cfg = pipe == null ? null : pipe.side(side);
        this.ghostCount = hasFilter(type, upgraded) ? SideConfig.MAX_FILTER : 0;

        for (int i = 0; i < ghostCount; i++) {
            addSlot(new GhostSlot(filterInv, i, FILTER_X + i * 18, FILTER_Y));
        }
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
        speedData = track(() -> cfg.speed.ordinal());
        matchData = track(() -> cfg.matchComponents ? 1 : 0);

        if (cfg != null) loadFilter();
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

    /** Energy pipes have no filter; other pipes need an upgrade on the side. */
    public static boolean hasFilter(PipeType type, boolean upgraded) {
        return type != PipeType.ENERGY && upgraded;
    }

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

    public SpeedTier speed() {
        return SpeedTier.byIndex(speedData.get());
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
                filterInv.clearContent();
                cfg.filter.clear();
            }
            default -> {
                return false;
            }
        }
        pipe.setChanged();
        return true;
    }

    // ---- ghost filter slots -------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < ghostCount && clickType != ClickType.QUICK_CRAFT) {
            clickGhost(slots.get(slotId), getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void clickGhost(Slot slot, ItemStack carried) {
        if (carried.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            if (!SideConfig.isValidSample(type, carried) || filterContains(carried)) return;
            slot.set(carried.copyWithCount(1));
        }
        saveFilter();
    }

    private boolean filterContains(ItemStack stack) {
        for (int i = 0; i < filterInv.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(filterInv.getItem(i), stack)) return true;
        }
        return false;
    }

    private void loadFilter() {
        for (int i = 0; i < cfg.filter.size() && i < filterInv.getContainerSize(); i++) {
            filterInv.setItem(i, cfg.filter.get(i).copy());
        }
    }

    private void saveFilter() {
        if (cfg == null) return;
        cfg.filter.clear();
        for (int i = 0; i < filterInv.getContainerSize(); i++) {
            ItemStack stack = filterInv.getItem(i);
            if (!stack.isEmpty()) cfg.filter.add(stack.copy());
        }
        pipe.setChanged();
    }

    // ---- menu plumbing ------------------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pipe == null) return true;
        return !pipe.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    /** A slot that only displays a filter entry: nothing can be put in or taken out by the vanilla logic. */
    private static class GhostSlot extends Slot {
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
    }
}
