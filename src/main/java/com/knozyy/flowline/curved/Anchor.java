package com.knozyy.flowline.curved;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Where a curved node is fixed: nowhere (in the air), on a face of a block pipe, or on a face of another block. */
public sealed interface Anchor permits Anchor.Free, Anchor.PipeFace, Anchor.BlockFace {
    Free FREE = new Free();

    record Free() implements Anchor {}

    /** On face {@code face} of the Flowline block pipe at {@code pos}: the face becomes a pipe connection. */
    record PipeFace(BlockPos pos, Direction face) implements Anchor {}

    /** On face {@code face} of the block at {@code pos}: a network end when that block holds what the pipe carries. */
    record BlockFace(BlockPos pos, Direction face) implements Anchor {}
}
