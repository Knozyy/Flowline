package com.knozyy.flowline.curve;

/**
 * Where a ray first meets a curved edge: the edge, the segment, the distance along the ray, the point, and how far
 * along the segment (0 at its start, 1 at its end) the hit lies.
 */
public record RayHit(long edgeId, int segment, double distance, Vec3d point, double along) {}
