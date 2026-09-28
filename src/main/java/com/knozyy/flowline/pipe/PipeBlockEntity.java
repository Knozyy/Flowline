package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.UpgradeItem;
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

import java.util.List;

public class PipeBlockEntity extends BlockEntity {
    private final SideConfig[] sides = new SideConfig[6];
    /** Bit per {@link Direction#ordinal()}: the wrench disconnected that side. */
    private int disconnected = 0;
    /** One upgrade per side, indexed by {@link Direction#ordinal()}. Edited through the side's GUI. */
    private final SimpleContainer upgrades = new SimpleContainer(6) {
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

    public Container upgrades() {
        return upgrades;
    }

    public ItemStack getUpgrade(Direction dir) {
        return upgrades.getItem(dir.ordinal());
    }

    /** @return the upgrade that was installed before. */
    public ItemStack setUpgrade(Direction dir, ItemStack upgrade) {
        ItemStack old = upgrades.getItem(dir.ordinal());
        upgrades.setItem(dir.ordinal(), upgrade);
        return old;
    }

    /** Takes the upgrade out of a side, e.g. when it stops extracting. */
    public ItemStack removeUpgrade(Direction dir) {
        return upgrades.removeItem(dir.ordinal(), 1);
    }

    /** The speed tier of each side mirrors the upgrade item in its slot. */
    private void syncUpgrades() {
        for (Direction dir : Direction.values()) {
            sides[dir.ordinal()].speed = UpgradeItem.tierOf(upgrades.getItem(dir.ordinal()));
        }
    }

    public PipeType type() {
        return ((PipeBlock) getBlockState().getBlock()).type();
    }

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

    public void serverTick(ServerLevel level) {
        // The level's state, not the cached one: connection updates must be visible here immediately.
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof PipeBlock)) return;
        long time = level.getGameTime();
        Boolean powered = null;
        for (Direction dir : Direction.values()) {
            SideConfig cfg = sides[dir.ordinal()];
            if (cfg.mode != SideMode.EXTRACT) continue;
            if (state.getValue(PipeBlock.prop(dir)) != Conn.ENDPOINT) continue;
            if (time % cfg.speed.interval != 0) continue;
            if (cfg.redstone != RedstoneMode.IGNORED) {
                if (powered == null) powered = level.hasNeighborSignal(worldPosition);
                if (!cfg.redstone.allows(powered)) continue;
            }

            List<PipeNetwork.Target> targets = PipeNetwork.collectTargets(level, worldPosition, dir, type(), cfg);
            if (targets.isEmpty()) continue;
            type().transfer(level, worldPosition.relative(dir), dir.getOpposite(), cfg, targets);
        }
    }

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
