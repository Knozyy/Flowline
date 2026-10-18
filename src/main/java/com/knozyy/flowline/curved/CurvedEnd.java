package com.knozyy.flowline.curved;

import com.knozyy.flowline.pipe.Port;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The end of a curved pipe fixed to a block face: the same {@link SideConfig} and upgrade slots as a block pipe's
 * side. It keeps them while its block is gone, like a block pipe side does.
 */
public final class CurvedEnd implements Port {
    private final long nodeId;
    private final PipeType type;
    private final Anchor.BlockFace anchor;
    private final SideConfig cfg;
    private final Runnable changed;
    /** Redstone signal seen on the last tick, for pulse mode's rising edges. */
    public boolean powered;
    private final SimpleContainer upgrades = new SimpleContainer(SideConfig.UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return SideUpgrades.accepts(cfg, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SideUpgrades.sync(cfg, this, 0);
            changed.run();
        }
    };

    public CurvedEnd(long nodeId, PipeType type, Anchor.BlockFace anchor, Runnable changed) {
        this.nodeId = nodeId;
        this.type = type;
        this.anchor = anchor;
        this.cfg = new SideConfig(type);
        this.changed = changed;
    }

    public long nodeId() {
        return nodeId;
    }

    public Anchor.BlockFace anchor() {
        return anchor;
    }

    public Container upgrades() {
        return upgrades;
    }

    /** Takes every upgrade out. */
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            ItemStack stack = upgrades.removeItemNoUpdate(i);
            if (!stack.isEmpty()) removed.add(stack);
        }
        SideUpgrades.sync(cfg, upgrades, 0);
        return removed;
    }

    /** Takes out the upgrades an inserting end cannot use (everything but Filter upgrades). */
    public List<ItemStack> takeExtractOnlyUpgrades() {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            ItemStack stack = upgrades.getItem(i);
            if (stack.isEmpty() || com.knozyy.flowline.item.UpgradeItem.typeOf(stack) == com.knozyy.flowline.item.UpgradeType.FILTER) {
                continue;
            }
            removed.add(upgrades.removeItemNoUpdate(i));
        }
        SideUpgrades.sync(cfg, upgrades, 0);
        return removed;
    }

    @Override
    public PipeType type() {
        return type;
    }

    @Override
    public SideConfig cfg() {
        return cfg;
    }

    @Override
    public BlockPos block() {
        return anchor.pos();
    }

    @Override
    public Direction access() {
        return anchor.face();
    }

    @Override
    public long order() {
        return Long.MAX_VALUE - nodeId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("cfg", cfg.save());
        NonNullList<ItemStack> items = NonNullList.withSize(upgrades.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) items.set(i, upgrades.getItem(i));
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), items));
        return tag;
    }

    public void load(CompoundTag tag) {
        cfg.load(tag.getCompound("cfg"));
        NonNullList<ItemStack> items = NonNullList.withSize(upgrades.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("upgrades"), items);
        for (int i = 0; i < items.size(); i++) upgrades.setItem(i, items.get(i));
        SideUpgrades.sync(cfg, upgrades, 0);
    }
}
