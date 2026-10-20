package com.knozyy.flowline.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Side {@code side} of a block pipe, as a network {@link Port}. */
public record PipeSidePort(PipeBlockEntity pipe, Direction side) implements Port {
    @Override
    public PipeType type() {
        return pipe.type();
    }

    @Override
    public SideConfig cfg() {
        return pipe.side(side);
    }

    @Override
    public BlockPos block() {
        return pipe.getBlockPos().relative(side);
    }

    @Override
    public Direction access() {
        return side.getOpposite();
    }

    @Override
    public long order() {
        return pipe.getBlockPos().asLong() * 8 + side.ordinal();
    }
}
