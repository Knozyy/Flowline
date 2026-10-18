package com.knozyy.flowline.curve;

/** The collision box of segment {@code segment} of edge {@code edgeId}. */
public record SegmentBox(long edgeId, int segment, Obb box) {}
