package com.knozyy.flowline.pipe;

import com.knozyy.flowline.util.Stacks;
import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PipeBlockEntity extends BlockEntity {
    /** No dye: connects to pipes of any colour. */
    public static final int NO_COLOR = -1;

    private final PipeType type;
    /** Redstone output, recomputed every tick from the sides; not saved, sides report again after loading. */
    private boolean emitting = false;
    private final SideConfig[] sides = new SideConfig[6];
    /** Bit per {@link Direction#ordinal()}: the wrench disconnected that side. */
    private int disconnected = 0;
    /** {@link net.minecraft.world.item.DyeColor} id, or {@link #NO_COLOR}. Pipes of two different colours never connect. */
    private int color = NO_COLOR;
    /** Redstone signal at the last neighbour update, for pulse mode's rising edges. */
    private boolean powered = false;
    /** {@link SideConfig#UPGRADE_SLOTS} upgrades per side; slot = side ordinal * UPGRADE_SLOTS + index. */
    private final SimpleContainer upgrades = new SimpleContainer(6 * SideConfig.UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(Direction.values()[slot / SideConfig.UPGRADE_SLOTS], stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            syncUpgrades();
            PipeBlockEntity.this.setChanged();
        }
    };

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
        this.type = state.getBlock() instanceof PipeBlock pipe ? pipe.type() : PipeType.ITEM;
        for (int i = 0; i < sides.length; i++) sides[i] = new SideConfig(type);
    }

    public SideConfig side(Direction dir) {
        return sides[dir.ordinal()];
    }

    public PipeType type() {
        return type;
    }

    public boolean menuValid(net.minecraft.world.entity.player.Player player) {
        return !isRemoved() && player.distanceToSqr(worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5) <= 64;
    }

    // ---- upgrades -----------------------------------------------------------------------------------------

    public Container upgrades() {
        return upgrades;
    }

    public static int upgradeSlot(Direction dir, int index) {
        return dir.ordinal() * SideConfig.UPGRADE_SLOTS + index;
    }

    public ItemStack getUpgrade(Direction dir, int index) {
        return upgrades.getItem(upgradeSlot(dir, index));
    }

    /** Extracting sides take every upgrade; inserting sides only Filter upgrades (for more insert rules). */
    public boolean accepts(Direction dir, ItemStack stack) {
        UpgradeType upgrade = UpgradeItem.typeOf(stack);
        if (upgrade == null) return false;
        return side(dir).mode == SideMode.EXTRACT || upgrade == UpgradeType.FILTER;
    }

    /** Puts one upgrade into the first free slot of a side. @return false if all slots are taken. */
    public boolean installUpgrade(Direction dir, ItemStack upgrade) {
        if (!accepts(dir, upgrade)) return false;
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            if (getUpgrade(dir, i).isEmpty()) {
                upgrades.setItem(upgradeSlot(dir, i), Stacks.withCount(upgrade, 1));
                return true;
            }
        }
        return false;
    }

    public int installedUpgrades(Direction dir) {
        int count = 0;
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            if (!getUpgrade(dir, i).isEmpty()) count++;
        }
        return count;
    }

    /** Takes every upgrade out of a side. */
    public List<ItemStack> removeAllUpgrades(Direction dir) {
        return removeUpgrades(dir, false);
    }

    /** Takes out the upgrades an inserting side cannot use (everything but Filter upgrades). */
    public List<ItemStack> removeExtractOnlyUpgrades(Direction dir) {
        return removeUpgrades(dir, true);
    }

    private List<ItemStack> removeUpgrades(Direction dir, boolean keepFilter) {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            if (keepFilter && UpgradeItem.typeOf(getUpgrade(dir, i)) == UpgradeType.FILTER) continue;
            ItemStack stack = upgrades.removeItem(upgradeSlot(dir, i), 1);
            if (!stack.isEmpty()) removed.add(stack);
        }
        return removed;
    }

    /** Each side's Speed, Stack and Filter counts mirror the upgrade items in its slots. */
    private void syncUpgrades() {
        for (Direction dir : Direction.values()) {
            SideConfig cfg = sides[dir.ordinal()];
            int speed = 0;
            int stack = 0;
            int filter = 0;
            for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
                UpgradeType type = UpgradeItem.typeOf(getUpgrade(dir, i));
                if (type != null) {
                    speed += type.speed;
                    stack += type.stack;
                    filter += type.filter;
                }
            }
            cfg.filterCount = filter;
            if (speed != cfg.speedCount || stack != cfg.stackCount) {
                cfg.speedCount = speed;
                cfg.stackCount = stack;
                cfg.wake();
            }
        }
    }

    // ---- connections, colour ----------------------------------------------------------------------------

    public boolean isDisconnected(Direction dir) {
        return (disconnected & (1 << dir.ordinal())) != 0;
    }

    public void setDisconnected(Direction dir, boolean value) {
        int bit = 1 << dir.ordinal();
        int updated = value ? disconnected | bit : disconnected & ~bit;
        if (updated != disconnected) {
            disconnected = updated;
            setChanged();
        }
    }

    public int color() {
        return color;
    }

    /** Whether two pipe colours may connect: equal, or at least one of them undyed. */
    public static boolean colorsMatch(int a, int b) {
        return a == NO_COLOR || b == NO_COLOR || a == b;
    }

    public void setColor(int value) {
        if (value == color) return;
        color = value;
        setChanged();
        sync();
    }

    /** Sends the colour to watching clients, which redraw the block. */
    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- ticking ------------------------------------------------------------------------------------------

    /** Something next to the pipe changed: let sleeping or slowed-down sides look again soon. */
    public void wake() {
        for (SideConfig cfg : sides) cfg.wake();
    }

    /** Neighbour update with the pipe's current redstone signal: a rising edge arms pulse-mode sides. */
    public void onSignal(boolean signal) {
        if (signal == powered) return;
        powered = signal;
        if (signal) {
            for (SideConfig cfg : sides) {
                if (cfg.redstone == RedstoneMode.PULSE && cfg.mode == SideMode.EXTRACT) cfg.pulsePending = true;
            }
        }
        setChanged();
    }

    public void serverTick(ServerLevel level) {
        // The level's state, not the cached one: connection updates must be visible here immediately.
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof PipeBlock)) return;
        Boolean signal = null;

        for (Direction dir : Direction.values()) {
            SideConfig cfg = sides[dir.ordinal()];
            if (cfg.mode != SideMode.EXTRACT || !state.getValue(PipeBlock.prop(dir)).isEndpoint()) {
                if (cfg.interval >= 0 || cfg.graph != null) cfg.resetRuntime();
                cfg.signalling = false;
                continue;
            }
            if (cfg.redstone == RedstoneMode.PULSE) {
                if (cfg.pulsePending) {
                    cfg.pulsePending = false;
                    long pulsed = operate(level, dir, cfg, 1);
                    cfg.lastMoved = pulsed;
                    cfg.lastBlocked = false;
                    cfg.signalling = signalAfter(level, dir, cfg, pulsed, false);
                }
                continue;
            }
            if (cfg.sleeping) {
                if (cfg.graph != null && cfg.graph.valid()) continue;
                cfg.sleeping = false;
                cfg.interval = Pacing.start(cfg.speedCount);
                cfg.cooldown = 1;
            }
            if (cfg.interval < 0) {
                // First run: stagger sides so pipes placed together do not all work on the same tick.
                cfg.interval = Pacing.start(cfg.speedCount);
                cfg.cooldown = 1 + level.random.nextInt(cfg.interval);
            }
            if (--cfg.cooldown > 0) continue;

            long moved = 0;
            boolean blocked = false;
            if (cfg.redstone != RedstoneMode.IGNORED) {
                if (signal == null) signal = level.hasNeighborSignal(worldPosition);
                blocked = !cfg.redstone.allows(signal);
            }
            if (!blocked) {
                moved = operate(level, dir, cfg, cfg.interval);
                if (moved < 0) {   // asleep
                    cfg.lastMoved = moved;
                    cfg.lastBlocked = false;
                    cfg.signalling = signalAfter(level, dir, cfg, moved, false);
                    continue;
                }
            }
            cfg.lastMoved = moved;
            cfg.lastBlocked = blocked;
            cfg.signalling = signalAfter(level, dir, cfg, moved, blocked);
            cfg.interval = moved <= 0 ? Pacing.afterIdle(cfg.interval, cfg.speedCount)
                    // energy flows every tick while there is work, like a cable: many sources only give one
                    // tick's worth per call, so a slower pipe would get less out of them
                    : type == PipeType.ENERGY ? 1
                    : Pacing.afterWork(cfg.interval, cfg.speedCount);
            cfg.cooldown = cfg.interval;
        }

        boolean emit = false;
        for (SideConfig cfg : sides) emit |= cfg.mode == SideMode.EXTRACT && cfg.signal != SignalMode.OFF && cfg.signalling;
        if (emit != emitting) {
            emitting = emit;
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    /**
     * The pipe lost its last attached block and no longer ticks (see {@link PipeBlock#getTicker}): what the tick
     * would have cleaned up is cleaned up now, and a redstone output stops.
     */
    public void stopTicking() {
        for (SideConfig cfg : sides) cfg.resetRuntime();
        if (emitting && level != null) {
            emitting = false;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** Whether a side's redstone output condition holds after an operation that moved {@code moved} (-1: asleep). */
    private boolean signalAfter(ServerLevel level, Direction dir, SideConfig cfg, long moved, boolean blocked) {
        return switch (cfg.signal) {
            case OFF -> false;
            case MOVING -> moved > 0;
            case STUCK -> {
                if (blocked || moved > 0) yield false;
                if (cfg.sourceCaps == null) {
                    cfg.sourceCaps = Caps.create(type, level, worldPosition.relative(dir), dir.getOpposite());
                }
                yield type.hasWork(cfg.sourceCaps, cfg);
            }
        };
    }

    /** Whether the pipe gives a redstone signal (see {@link SideConfig#signal}). */
    public boolean emitsSignal() {
        return emitting;
    }

    /** Whether any side has a redstone output, so redstone dust should connect to the pipe. */
    public boolean hasSignalOutput() {
        for (SideConfig cfg : sides) {
            if (cfg.mode == SideMode.EXTRACT && cfg.signal != SignalMode.OFF) return true;
        }
        return false;
    }

    /**
     * One operation of an extracting side.
     *
     * @return amount moved, or -1 if there is no target and the side went to sleep
     */
    private long operate(ServerLevel level, Direction dir, SideConfig cfg, int elapsed) {
        List<PipeNetwork.Target> targets = PipeNetwork.targets(level, worldPosition, dir, type, cfg);
        if (targets.isEmpty()) {
            cfg.sleeping = true;
            return -1;
        }
        BlockPos source = worldPosition.relative(dir);
        if (cfg.sourceCaps == null) cfg.sourceCaps = Caps.create(type, level, source, dir.getOpposite());
        return type.transfer(level, source, cfg.sourceCaps, cfg, targets, elapsed);
    }

    // ---- world hooks --------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) PipeNetwork.invalidate(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) PipeNetwork.invalidate(level, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) PipeNetwork.invalidate(level, worldPosition);
    }

    // ---- persistence and client sync ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (SideConfig cfg : sides) list.add(cfg.save());
        tag.put("sides", list);
        tag.putInt("disconnected", disconnected);
        tag.putBoolean("powered", powered);
        NonNullList<ItemStack> items = NonNullList.withSize(upgrades.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) items.set(i, upgrades.getItem(i));
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), items));
        saveVisuals(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < sides.length && i < list.size(); i++) sides[i].load(list.getCompound(i));
        disconnected = tag.getInt("disconnected");
        powered = tag.getBoolean("powered");
        // slot by slot: upgrade positions belong to sides (slot = side * UPGRADE_SLOTS + index)
        NonNullList<ItemStack> items = NonNullList.withSize(upgrades.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("upgrades"), items);
        for (int i = 0; i < items.size(); i++) upgrades.setItem(i, items.get(i));
        syncUpgrades();
        loadVisuals(tag);
    }

    private void saveVisuals(CompoundTag tag) {
        if (color != NO_COLOR) tag.putInt("color", color);
    }

    private void loadVisuals(CompoundTag tag) {
        color = tag.contains("color") ? tag.getInt("color") : NO_COLOR;
    }

    /** Clients only need what changes the look: the colour. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveVisuals(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        loadVisuals(tag);
        redraw();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) loadVisuals(tag);
        redraw();
    }

    /** Client: the colour changed, rebuild the chunk mesh with the new tint. */
    private void redraw() {
        if (level == null || !level.isClientSide) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }
}
