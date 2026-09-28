package com.knozyy.flowline.pipe;

import com.knozyy.flowline.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class PipeBlockEntity extends BlockEntity {
    private final SideConfig[] sides = new SideConfig[6];
    /** Bit per {@link Direction#ordinal()}: the wrench disconnected that side. */
    private int disconnected = 0;

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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < sides.length && i < list.size(); i++) sides[i].load(list.getCompound(i), registries);
        disconnected = tag.getInt("disconnected");
    }
}
