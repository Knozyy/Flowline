package com.knozyy.flowline.curve;

import java.util.List;

/** One solved span: its points from node to node, and the common length of its segments. */
public record SolvedSpan(List<Vec3d> points, double step) {
    public SolvedSpan {
        points = List.copyOf(points);
    }

    public int segments() {
        return points.size() - 1;
    }

    /** Pipe items the span uses: its length rounded up, {@code ceil(n·h)} as in Curvy Pipes. */
    public int cost() {
        return (int) Math.ceil(segments() * step);
    }
}
