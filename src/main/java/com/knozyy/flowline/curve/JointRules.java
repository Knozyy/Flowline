package com.knozyy.flowline.curve;

/**
 * The rules for where curved edges meet. They use round-pipe formulas fed with Flowline's own diameter.
 */
public final class JointRules {
    private JointRules() {}

    public static double pipeRadius(double diameter) {
        return 0.5 * diameter;
    }

    /** Half the widest edge at the node; a joint of three or more edges, or a forced one, is √2 times larger. */
    public static double jointRadius(double[] diameters, boolean forceJoint) {
        double widest = 0;
        for (double d : diameters) widest = Math.max(widest, d);
        double radius = 0.5 * widest;
        return diameters.length >= 3 || forceJoint ? radius * Math.sqrt(2) : radius;
    }

    /** A lone end, or two edges of different widths meeting: drawn as an end piece or a transition. */
    public static boolean endOrTransition(double[] diameters, boolean forceJoint) {
        if (forceJoint) return false;
        return diameters.length == 1 || diameters.length == 2 && diameters[0] != diameters[1];
    }

    /** {@code node_in_joint}: the far end of an edge lies inside the joint. */
    public static boolean nodeInJoint(Vec3d node, Vec3d farEnd, double jointRadius) {
        return farEnd.distanceSq(node) < jointRadius * jointRadius;
    }

    /**
     * {@code angle_too_small} for two unit exit directions {@code a} and {@code b} at one node, with pipe radii
     * {@code ra}, {@code rb} and the node's joint radius.
     */
    public static boolean angleTooSmall(Vec3d a, Vec3d b, double ra, double rb, double jointRadius) {
        double d = a.dot(b);
        if (d <= CurveRules.ANGLE_FREE) return false;
        if (d >= CurveRules.ANGLE_SAME) return true;
        double t = (d * rb + ra) / a.cross(b).length();
        return t * t + rb * rb > Math.max(4 * jointRadius * jointRadius, CurveRules.JOINT_MIN_SQ);
    }
}
