package com.knozyy.flowline.curve;

/**
 * Numbers of the curved pipe engine.
 */
public final class CurveRules {
    /** Flowline: the curved duct is as wide as the block pipes' 6/16 channel. Also the solver's sampling spacing. */
    public static final double DIAMETER = 6 / 16.0;
    public static final double RADIUS = DIAMETER / 2;

    /** Span layout: at most this many base segments, and spans shorter than this times the spacing are refused. */
    public static final int MAX_BASE_SEGMENTS = 64;
    public static final double MIN_SPAN_FACTOR = 0.5;

    /** Solver: converged below this sum of squared residuals; damped Newton step; first iterations always taken. */
    public static final double CONVERGED = 0.01;
    public static final double STEP = 0.5;
    public static final int FREE_ITERATIONS = 4;
    /** A safety stop for chains that never settle. */
    public static final int MAX_ITERATIONS = 200;
    public static final double REGULARISATION = 1e-8;

    /** A node on a block face must lie within this of one of the block's outline faces. */
    public static final double FACE_TOLERANCE = 0.01;
    /** Positions farther than the player's block reach plus this from the eyes are too far. */
    public static final double REACH_MARGIN = 1.0;

    /** Joint angle rule: exit directions whose dot is at most ANGLE_FREE pass, at least ANGLE_SAME fail. */
    public static final double ANGLE_FREE = 0.1;
    public static final double ANGLE_SAME = 0.999;
    public static final double JOINT_MIN_SQ = 0.25;

    /** Two boxes intersect only when they overlap by more than this on every axis: touching is not intersecting. */
    public static final double OVERLAP_EPS = 1e-6;

    private CurveRules() {}
}
