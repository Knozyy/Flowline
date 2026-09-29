package com.knozyy.flowline.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/**
 * One capability of one block face, looked up once and kept until Forge invalidates it (the block entity was removed
 * or replaced, or the capability changed); then it is looked up again on the next use.
 */
public final class CapCache<T> {
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction access;
    private final Capability<T> capability;
    @Nullable
    private LazyOptional<T> cached;

    public CapCache(Capability<T> capability, ServerLevel level, BlockPos pos, Direction access) {
        this.capability = capability;
        this.level = level;
        this.pos = pos.immutable();
        this.access = access;
    }

    @Nullable
    public T get() {
        if (cached == null || !cached.isPresent()) {
            if (!level.isLoaded(pos)) return null;
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                cached = null;
                return null;
            }
            cached = be.getCapability(capability, access);
        }
        return cached.resolve().orElse(null);
    }
}
