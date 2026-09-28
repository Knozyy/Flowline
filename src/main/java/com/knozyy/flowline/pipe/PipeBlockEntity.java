package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.UpgradeItem;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.ArrayList;
import java.util.List;

public class PipeBlockEntity extends BlockEntity {
    private final SideConfig[] sides = new SideConfig[6];
    /** Bit per {@link Direction#ordinal()}: the wrench disconnected that side. */
    private int disconnected = 0;
    /** {@link SideConfig#UPGRADE_SLOTS} upgrades per side; slot = side ordinal * UPGRADE_SLOTS + index. */
    private final SimpleContainer upgrades = new SimpleContainer(6 * SideConfig.UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem;
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
        for (int i = 0; i < sides.length; i++) sides[i] = new SideConfig();
    }

    public SideConfig side(Direction dir) {
        return sides[dir.ordinal()];
    }

    public PipeType type() {
        return ((PipeBlock) getBlockState().getBlock()).type();
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

    /** Puts one upgrade into the first free slot of a side. @return false if all slots are taken. */
    public boolean installUpgrade(Direction dir, ItemStack upgrade) {
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

    /** Takes every upgrade out of a side, e.g. when it stops extracting. */
    public List<ItemStack> removeAllUpgrades(Direction dir) {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < SideConfig.UPGRADE_SLOTS; i++) {
            ItemStack stack = upgrades.removeItem(upgradeSlot(dir, i), 1);
            if (!stack.isEmpty()) removed.add(stack);
        }
        return removed;
    }

    /** Each side's Speed and Stack counts mirror the upgrade items in its slots. */
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

    // ---- connections --------------------------------------------------------------------------------------

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

    // ---- ticking ------------------------------------------------------------------------------------------

    /** Something next to the pipe changed: let sleeping or slowed-down sides look again soon. */
    public void wake() {
        for (SideConfig cfg : sides) cfg.wake();
    }

    public void serverTick(ServerLevel level) {
        // The level's state, not the cached one: connection updates must be visible here immediately.
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof PipeBlock)) return;
        long version = PipeNetwork.version();
        Boolean powered = null;

        for (Direction dir : Direction.values()) {
            SideConfig cfg = sides[dir.ordinal()];
            if (cfg.mode != SideMode.EXTRACT || state.getValue(PipeBlock.prop(dir)) != Conn.ENDPOINT) {
                if (cfg.interval >= 0) cfg.resetRuntime();
                continue;
            }
            if (cfg.sleeping) {
                if (cfg.sleepVersion == version) continue;
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

            int moved = 0;
            boolean blocked = false;
            if (cfg.redstone != RedstoneMode.IGNORED) {
                if (powered == null) powered = level.hasNeighborSignal(worldPosition);
                blocked = !cfg.redstone.allows(powered);
            }
            if (!blocked) {
                List<PipeNetwork.Target> targets = PipeNetwork.targets(level, worldPosition, dir, type(), cfg);
                if (targets.isEmpty()) {
                    cfg.sleeping = true;
                    cfg.sleepVersion = version;
                    continue;
                }
                if (cfg.sourceCache == null) {
                    cfg.sourceCache = BlockCapabilityCache.create(type().capability(), level,
                            worldPosition.relative(dir), dir.getOpposite());
                }
                moved = type().transfer(level, worldPosition.relative(dir), cfg.sourceCache, cfg, targets);
            }
            cfg.interval = moved > 0
                    ? Pacing.afterWork(cfg.interval, cfg.speedCount)
                    : Pacing.afterIdle(cfg.interval, cfg.speedCount);
            cfg.cooldown = cfg.interval;
        }
    }

    // ---- persistence --------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (SideConfig cfg : sides) list.add(cfg.save(registries));
        tag.put("sides", list);
        tag.putInt("disconnected", disconnected);
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), upgrades.getItems(), registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < sides.length && i < list.size(); i++) sides[i].load(list.getCompound(i), registries);
        disconnected = tag.getInt("disconnected");
        upgrades.getItems().clear();
        ContainerHelper.loadAllItems(tag.getCompound("upgrades"), upgrades.getItems(), registries);
        syncUpgrades();
    }
}
