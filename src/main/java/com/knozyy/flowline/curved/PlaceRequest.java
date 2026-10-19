package com.knozyy.flowline.curved;

import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** A player's request to lay a curved pipe of {@code type} from {@code start} through {@code internals} to {@code end}. */
public record PlaceRequest(PipeType type, End start, End end, List<Vec3> internals) {
    public PlaceRequest {
        internals = List.copyOf(internals);
    }

    /**
     * A chain end: a point, optionally an existing node to continue from, a block face to fix to, or a curved edge to
     * split there (the chain then branches off the new node).
     */
    public record End(Vec3 pos, OptionalLong node, Optional<Face> face, Optional<Split> split) {
        public End(Vec3 pos, OptionalLong node, Optional<Face> face) {
            this(pos, node, face, Optional.empty());
        }
    }

    /** Curved edge {@code edge}, split {@code along} (0 to 1) its segment {@code segment}. */
    public record Split(long edge, int segment, double along) {}

    /** Face {@code dir} of the block at {@code pos}. */
    public record Face(BlockPos pos, Direction dir) {}
}
