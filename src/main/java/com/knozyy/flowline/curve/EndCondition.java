package com.knozyy.flowline.curve;

/**
 * How a chain end leaves its node: free (in the air) or along a fixed unit direction (a block face, a pipe end, an
 * extension). The direction points along the chain: away from the first node at the start, into the last node at the
 * end.
 */
public record EndCondition(Vec3d direction) {
    public static final EndCondition FREE = new EndCondition(null);

    public boolean fixed() {
        return direction != null;
    }
}
