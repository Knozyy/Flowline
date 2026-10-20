package com.knozyy.flowline.curve;

/** Curvy Pipes' {@code too_far}: farther than the player's block reach plus one block from the eyes. */
public final class Reach {
    private Reach() {}

    public static boolean tooFar(Vec3d eye, double blockReach, Vec3d p) {
        double limit = blockReach + CurveRules.REACH_MARGIN;
        return p.distanceSq(eye) > limit * limit;
    }
}
