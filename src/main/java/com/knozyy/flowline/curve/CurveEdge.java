package com.knozyy.flowline.curve;

/** A solved curved edge between two nodes; {@code shape}'s points run from {@code from} to {@code to}. */
public record CurveEdge(long id, long from, long to, double diameter, SolvedSpan shape) {}
