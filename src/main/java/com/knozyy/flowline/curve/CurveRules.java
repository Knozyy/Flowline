package com.knozyy.flowline.curve;

/**
 * Numbers of the curved pipe engine. Curvy Pipes 1.15.8's values come from the static behaviour reports
 * (CurvyAdditionForFlowLine/docs/curvy-behaviour-addendum-re.md); Flowline's own choices say so.
 */
public final class CurveRules {
    /** Two boxes intersect only when they overlap by more than this on every axis: touching is not intersecting. */
    public static final double OVERLAP_EPS = 1e-6;

    private CurveRules() {}
}
