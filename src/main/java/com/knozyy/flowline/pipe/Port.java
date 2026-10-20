package com.knozyy.flowline.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One end of a pipe network that touches a block: a side of a block pipe, or the end of a curved pipe. It owns the
 * {@link SideConfig} that decides what moves through it.
 */
public interface Port {
    PipeType type();

    SideConfig cfg();

    /** The block this end is attached to. */
    BlockPos block();

    /** The face of {@link #block()} the end touches. */
    Direction access();

    /** A stable key that orders ends of equal distance. */
    long order();
}
