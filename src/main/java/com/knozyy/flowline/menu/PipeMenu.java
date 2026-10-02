package com.knozyy.flowline.menu;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.network.FilterSyncPayload;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.Distribution;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SideStatus;
import com.knozyy.flowline.pipe.SignalMode;
import com.knozyy.flowline.registry.ModMenus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * Configuration screen for one side of a pipe (extracting or inserting). The server owns the {@link SideConfig}; the
 * client mirrors its scalar values through data slots, its filter through {@link FilterSyncPayload} and its upgrades
 * through real slots.
 *
 * <p>Slot layout: 0..5 = upgrades, then the player inventory. Inserting sides only take Filter upgrades. Filter rules
 * are not slots: the screen draws them as a list and edits them through packets.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int BTN_DISTRIBUTION = 1;
    public static final int BTN_REDSTONE = 2;
    public static final int BTN_CLEAR = 4;
    public static final int BTN_DISTRIBUTION_BACK = 8;
    public static final int BTN_REDSTONE_BACK = 9;
    /** Toggles channel {@code id - BTN_CHANNEL} on universal pipes: 0 items, 1 fluids, 2 energy. */
    public static final int BTN_CHANNEL = 10;
    /** Insert sides: toggle overflow. */
    public static final int BTN_OVERFLOW = 13;
    /** Extract sides: cycle the redstone output (back: the other way). */
    public static final int BTN_SIGNAL = 14;
    public static final int BTN_SIGNAL_BACK = 15;
    /** Opens the screen of side {@code Direction.from3DDataValue(id - BTN_SIDE)} of the same pipe. */
    public static final int BTN_SIDE = 20;

    /** Fields set through {@link com.knozyy.flowline.network.SetSideValuePayload}. */
    public static final int FIELD_PRIORITY = 0;
    public static final int FIELD_LIMIT = 1;
    public static final int FIELD_RATE = 2;

    /** Screen size and the slot positions inside it. */
    public static final int WIDTH = 310, HEIGHT = 230;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 150, HOTBAR_Y = 208;
    /** Top-left of the 3x2 upgrade grid, right of the player inventory. */
    public static final int UPGRADE_X = 180, UPGRADE_Y = 150;

    /** Largest network form of one rule; bigger ones (a rule copied from a stack full of data) are refused. */
    public static final int MAX_RULE_BYTES = 16 * 1024;
    /** Payload budget of one filter sync packet. */
    public static final int MAX_SYNC_BYTES = 256 * 1024;

    private static final int UPGRADES = SideConfig.UPGRADE_SLOTS;

    public final BlockPos pos;
    public final Direction side;
    public final PipeType type;

    /** Null on the client. */
    private final PipeBlockEntity pipe;
    private final SideConfig cfg;

    /** Server: the player to send filter changes to. */
    @Nullable
    private final ServerPlayer player;
    private final HolderLookup.Provider registries;
    /** Server: the rules as the client last got them, and the filter version they were taken at. */
    private final List<FilterEntry> sentFilter = new ArrayList<>();
    private int sentVersion = Integer.MIN_VALUE;
    /** Client: the side's rules by position, as synced by the server. */
    private final List<FilterEntry> clientFilter = new ArrayList<>();
    private final int inventoryStart;
    /** The mode the side had when the menu opened (sent with the open packet); the menu closes if it changes. */
    private final SideMode openedMode;
    /** Server: whether the source holds work, cached because finding out scans the source. */
    private boolean hasWork;
    private long hasWorkCheckedAt = Long.MIN_VALUE;

    private final DataSlot modeData;
    private final DataSlot overflowData;
    private final DataSlot signalData;
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
    private final DataSlot statusData;
    private final DataSlot capacityData;
    private final DataSlot priorityData;
    private final DataSlot channelsData;
    private final IntSupplier limitData;
    private final IntSupplier rateData;
    private final IntSupplier itemsData;
    private final IntSupplier fluidData;
    private final IntSupplier chemicalData;

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
        this.openedMode = mode;

        // The client mirrors the whole upgrade container so slot indices match the server's.
        Container upgrades = pipe != null ? pipe.upgrades() : new SimpleContainer(6 * UPGRADES);
        for (int i = 0; i < UPGRADES; i++) {
            addSlot(new UpgradeSlot(upgrades, PipeBlockEntity.upgradeSlot(side, i),
                    UPGRADE_X + (i % 3) * 18, UPGRADE_Y + (i / 3) * 18));
        }
        inventoryStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
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
        statusData = track(() -> serverStatus().ordinal());
        capacityData = track(() -> cfg.filterCapacity());
        priorityData = track(() -> cfg.priority);
        channelsData = track(() -> cfg.channels);
        overflowData = track(() -> cfg.overflow ? 1 : 0);
        signalData = track(() -> cfg.signal.ordinal());
        limitData = trackInt(() -> cfg.limit);
        rateData = trackInt(() -> cfg.rate);
        itemsData = trackInt(() -> Pacing.itemsPerOperation(cfg.stackCount));
        fluidData = trackInt(() -> Pacing.fluidPerOperation(cfg.stackCount));
        chemicalData = trackInt(() -> Pacing.chemicalPerOperation(cfg.stackCount));
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

    /** Energy and chemical pipes have no filter. */
    public boolean hasFilter() {
        return type.hasFilter();
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

    public SideStatus status() {
        return SideStatus.byOrdinal(statusData.get());
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

    /** Millibuckets of fluid one operation of this side may move. */
    public int fluidPerOperation() {
        return fluidData.getAsInt();
    }

    /** Millibuckets of chemicals one operation of this side may move. */
    public int chemicalPerOperation() {
        return chemicalData.getAsInt();
    }

    public int channels() {
        return channelsData.get();
    }

    public SignalMode signal() {
        SignalMode[] modes = SignalMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, signalData.get()))];
    }

    public boolean overflow() {
        return overflowData.get() != 0;
    }

    // ---- client filter --------------------------------------------------------------------------------------

    /** Client: the rule at {@code index}, or null. */
    @Nullable
    public FilterEntry clientEntry(int index) {
        return index >= 0 && index < clientFilter.size() ? clientFilter.get(index) : null;
    }

    /** Client: rules by position, holes as null. May reach past {@link #capacity()}. */
    public List<FilterEntry> clientFilter() {
        return Collections.unmodifiableList(clientFilter);
    }

    /** Client: the first usable position without a rule, or -1 when the filter is full. */
    public int firstFreeClient() {
        for (int i = 0; i < capacity(); i++) {
            if (clientEntry(i) == null) return i;
        }
        return -1;
    }

    /** Client: rules in the usable positions. */
    public int ruleCount() {
        int count = 0;
        for (int i = 0; i < Math.min(capacity(), clientFilter.size()); i++) {
            if (clientFilter.get(i) != null) count++;
        }
        return count;
    }

    /** Client: apply a sync from the server. */
    public void receiveFilter(FilterSyncPayload payload) {
        if (payload.reset()) clientFilter.clear();
        for (FilterSyncPayload.Change change : payload.changes()) {
            while (clientFilter.size() <= change.index()) clientFilter.add(null);
            clientFilter.set(change.index(), change.entry().orElse(null));
        }
        while (!clientFilter.isEmpty() && clientFilter.get(clientFilter.size() - 1) == null) {
            clientFilter.remove(clientFilter.size() - 1);
        }
    }

    // ---- buttons ------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cfg == null) return false;
        if (id >= BTN_SIDE && id < BTN_SIDE + 6) return openSide(player, Direction.from3DDataValue(id - BTN_SIDE));
        switch (id) {
            case BTN_DISTRIBUTION -> cfg.distribution = cfg.distribution.next();
            case BTN_DISTRIBUTION_BACK -> cfg.distribution = cfg.distribution.previous();
            case BTN_REDSTONE -> {
                cfg.redstone = cfg.redstone.next();
                cfg.wake();
            }
            case BTN_REDSTONE_BACK -> {
                cfg.redstone = cfg.redstone.previous();
                cfg.wake();
            }
            case BTN_CHANNEL, BTN_CHANNEL + 1, BTN_CHANNEL + 2 -> {
                if (!type.hasChannels()) return false;
                cfg.channels ^= 1 << (id - BTN_CHANNEL);
                cfg.wake();
            }
            case BTN_SIGNAL, BTN_SIGNAL_BACK -> {
                if (cfg.mode != SideMode.EXTRACT) return false;
                cfg.signal = id == BTN_SIGNAL ? cfg.signal.next() : cfg.signal.previous();
                // redstone dust next to the pipe reconsiders whether it points at it
                pipe.getBlockState().updateNeighbourShapes(pipe.getLevel(), pipe.getBlockPos(), 3);
            }
            case BTN_OVERFLOW -> {
                if (cfg.mode != SideMode.INSERT) return false;
                cfg.overflow = !cfg.overflow;
            }
            case BTN_CLEAR -> {
                if (!hasFilter()) return false;
                cfg.clearFilter();
            }
            default -> {
                return false;
            }
        }
        pipe.setChanged();
        return true;
    }

    /** Whether {@code dir} of the pipe at {@code state} has a block attached, so it has a screen of its own. */
    public static boolean canOpenSide(BlockState state, Direction dir) {
        return state.getBlock() instanceof PipeBlock && state.getValue(PipeBlock.prop(dir)).isEndpoint();
    }

    /** Server: replace this menu by the one of another side of the same pipe, without closing the screen first. */
    private boolean openSide(Player player, Direction dir) {
        if (dir == side || !(player instanceof ServerPlayer serverPlayer) || serverPlayer.containerMenu != this
                || !pipe.menuValid(player) || pipe.getLevel() == null) {
            return false;
        }
        BlockState state = pipe.getLevel().getBlockState(pos);
        if (!canOpenSide(state, dir)) return false;
        Conn conn = state.getValue(PipeBlock.prop(dir));
        // no close packet: the client goes straight from this screen to the next one
        serverPlayer.doCloseContainer();
        PipeBlock.openConfig(pipe, dir, conn, serverPlayer);
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

    // ---- filter rules -------------------------------------------------------------------------------------

    /**
     * Server: replace one rule (null removes it). {@code index} -1 means the first free position. Invalid,
     * oversized and duplicate rules are refused, the last two with a message.
     */
    public void setEntry(int index, @Nullable FilterEntry entry) {
        if (cfg == null || !hasFilter()) return;
        if (index == -1) {
            if (entry == null) return;
            index = firstFree(cfg);
            if (index < 0) {
                message(Component.translatable("message.flowline.filter_full"));
                return;
            }
        }
        if (index < 0 || index >= cfg.filterCapacity()) return;
        if (entry != null) {
            if (entry.problem(type) != null) return;
            if (encodedSize(entry) > MAX_RULE_BYTES) {
                message(Component.translatable("message.flowline.rule_too_large"));
                return;
            }
            for (int i = 0; i < cfg.filterCapacity(); i++) {
                FilterEntry other = cfg.getEntry(i);
                if (i != index && other != null && other.sameMatch(entry)) {
                    message(Component.translatable("message.flowline.rule_exists", i + 1));
                    return;
                }
            }
        }
        cfg.setEntry(index, entry);
        pipe.setChanged();
    }

    /** Server: turn the carried stack into a rule at {@code index} (-1: first free position). */
    public void ruleFromCarried(int index) {
        ItemStack carried = getCarried();
        if (cfg == null || carried.isEmpty()) return;
        FilterEntry entry = FilterEntry.fromStack(type, carried, registries);
        if (entry != null) setEntry(index, entry);
    }

    private void message(Component text) {
        if (player != null) player.displayClientMessage(text, true);
    }

    /** The first usable position of {@code cfg}'s filter without a rule, or -1. */
    public static int firstFree(SideConfig cfg) {
        for (int i = 0; i < cfg.filterCapacity(); i++) {
            if (cfg.getEntry(i) == null) return i;
        }
        return -1;
    }

    /** Bytes {@code entry} takes in a packet. */
    public static int encodedSize(FilterEntry entry) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            entry.write(buf);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /**
     * The positions whose rule differs between {@code sent} and {@code cfg}, which is then brought up to date.
     * Positions past {@link FilterSyncPayload#MAX_INDEX} are never synced.
     */
    public static List<FilterSyncPayload.Change> diffFilter(List<FilterEntry> sent, SideConfig cfg) {
        List<FilterSyncPayload.Change> changes = new ArrayList<>();
        int end = Math.min(Math.max(sent.size(), cfg.filterSize()), FilterSyncPayload.MAX_INDEX + 1);
        for (int i = 0; i < end; i++) {
            FilterEntry now = cfg.getEntry(i);
            FilterEntry before = i < sent.size() ? sent.get(i) : null;
            if (Objects.equals(now, before)) continue;
            changes.add(new FilterSyncPayload.Change(i, Optional.ofNullable(now)));
            while (sent.size() <= i) sent.add(null);
            sent.set(i, now);
        }
        return changes;
    }

    /** Splits changes into packets of at most {@link FilterSyncPayload#MAX_CHANGES} entries and {@code maxBytes}. */
    public static List<List<FilterSyncPayload.Change>> packets(List<FilterSyncPayload.Change> changes, int maxBytes) {
        List<List<FilterSyncPayload.Change>> packets = new ArrayList<>();
        List<FilterSyncPayload.Change> current = new ArrayList<>();
        int bytes = 0;
        for (FilterSyncPayload.Change change : changes) {
            int size = change.entry().map(PipeMenu::encodedSize).orElse(0) + 8;
            if (!current.isEmpty() && (current.size() == FilterSyncPayload.MAX_CHANGES || bytes + size > maxBytes)) {
                packets.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(change);
            bytes += size;
        }
        if (!current.isEmpty()) packets.add(current);
        return packets;
    }

    /** Sends the rules that changed since the last sync, e.g. after an edit, a Configuration Card or another player. */
    private void syncFilter() {
        if (cfg == null || player == null || !hasFilter() || sentVersion == cfg.filterVersion()) return;
        boolean reset = sentVersion == Integer.MIN_VALUE;
        sentVersion = cfg.filterVersion();
        List<List<FilterSyncPayload.Change>> packets = packets(diffFilter(sentFilter, cfg), MAX_SYNC_BYTES);
        if (packets.isEmpty() && reset) packets.add(List.of());
        for (int i = 0; i < packets.size(); i++) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new FilterSyncPayload(containerId, reset && i == 0, packets.get(i)));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        syncFilter();
    }

    // ---- status -------------------------------------------------------------------------------------------

    private SideStatus serverStatus() {
        if (cfg.mode != SideMode.EXTRACT) return SideStatus.STARTING;
        return SideStatus.of(cfg, this::sourceHasWork);
    }

    /** Whether the source holds something this side would move; looked up at most once a second. */
    private boolean sourceHasWork() {
        if (!(pipe.getLevel() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        if (now - hasWorkCheckedAt >= 20) {
            hasWorkCheckedAt = now;
            if (cfg.sourceCaps == null) cfg.sourceCaps = Caps.create(type, level, pos.relative(side), side.getOpposite());
            hasWork = type.hasWork(cfg.sourceCaps, cfg);
        }
        return hasWork;
    }

    // ---- menu plumbing ------------------------------------------------------------------------------------

    /**
     * Shift-click moves upgrades between the player inventory and the upgrade slots; any other stack in the
     * inventory becomes a filter rule in the first free position (the stack itself stays where it is).
     */
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
            if (index >= inventoryStart && cfg != null && hasFilter()) {
                FilterEntry entry = FilterEntry.fromStack(type, stack, registries);
                if (entry != null) setEntry(-1, entry);
            }
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
        return pipe.menuValid(player) && cfg.mode == openedMode;
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
}
