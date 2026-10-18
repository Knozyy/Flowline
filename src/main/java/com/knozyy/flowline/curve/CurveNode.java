package com.knozyy.flowline.curve;

/** A node of the curve graph: a chain end, a bend point, a branch or a joint. */
public record CurveNode(long id, Vec3d pos, boolean forceJoint) {}
