package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PipeBlockEntity extends BlockEntity {
    /** Model data: the block a facade makes the pipe look like. */
    public static final ModelProperty<BlockState> FACADE = new ModelProperty<>();
    /** No dye: connects to pipes of any colour. */
    public static final int NO_COLOR = -1;

    private final PipeType type;
    private final SideConfig[] sides = new SideConfig[6];
    /** Bit per {@link Direction#ordinal()}: the wrench disconnected that side. */
    private int disconnected = 0;
    /** {@link net.minecraft.world.item.DyeColor} id, or {@link #NO_COLOR}. Pipes of two different colours never connect. */
    private int color = NO_COLOR;
    /** Block the pipe is hidden behind, or null. */
    @Nullable
    private BlockState facade = null;
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
                upgrades.setItem(upgradeSlot(dir, i), upgrade.copyWithCount(1));
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

    // ---- connections, colour, facade ----------------------------------------------------------------------

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

    @Nullable
    public BlockState facade() {
        return facade;
    }

    public void setFacade(@Nullable BlockState value) {
        if (value == facade) return;
        facade = value;
        setChanged();
        sync();
    }

    /** Sends the colour and facade to watching clients, which redraw the block. */
    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ModelData getModelData() {
        return facade == null ? ModelData.EMPTY : ModelData.builder().with(FACADE, facade).build();
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
                continue;
            }
            if (cfg.redstone == RedstoneMode.PULSE) {
                if (cfg.pulsePending) {
                    cfg.pulsePending = false;
                    operate(level, dir, cfg, 1);
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
                if (moved < 0) continue;   // asleep
            }
            cfg.interval = moved > 0
                    ? Pacing.afterWork(cfg.interval, cfg.speedCount)
                    : Pacing.afterIdle(cfg.interval, cfg.speedCount);
            cfg.cooldown = cfg.interval;
        }
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (SideConfig cfg : sides) list.add(cfg.save(registries));
        tag.put("sides", list);
        tag.putInt("disconnected", disconnected);
        tag.putBoolean("powered", powered);
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), upgrades.getItems(), registries));
        saveVisuals(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < sides.length && i < list.size(); i++) sides[i].load(list.getCompound(i), registries);
        disconnected = tag.getInt("disconnected");
        powered = tag.getBoolean("powered");
        upgrades.getItems().clear();
        ContainerHelper.loadAllItems(tag.getCompound("upgrades"), upgrades.getItems(), registries);
        syncUpgrades();
        loadVisuals(tag, registries);
    }

    private void saveVisuals(CompoundTag tag) {
        if (color != NO_COLOR) tag.putInt("color", color);
        if (facade != null) tag.put("facade", NbtUtils.writeBlockState(facade));
    }

    private void loadVisuals(CompoundTag tag, HolderLookup.Provider registries) {
        color = tag.contains("color") ? tag.getInt("color") : NO_COLOR;
        facade = tag.contains("facade")
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("facade"))
                : null;
        if (facade != null && facade.isAir()) facade = null;
    }

    /** Clients only need what changes the look: colour and facade. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
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
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadVisuals(tag, registries);
        redraw();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        loadVisuals(pkt.getTag(), registries);
        redraw();
    }

    /** Client: the facade or colour changed, rebuild the chunk mesh with the new model data and tint. */
    private void redraw() {
        if (level == null || !level.isClientSide) return;
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }
}
