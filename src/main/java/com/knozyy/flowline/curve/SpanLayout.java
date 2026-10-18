package com.knozyy.flowline.curve;

import java.util.Optional;

/**
 * How many equal segments a span between two nodes is cut into, and their starting length. {@code base} comes from
 * the span's length plus a slack term for each fixed end; {@code extra} is one more segment per fixed end, the one
 * that leaves along the fixed direction.
 */
public record SpanLayout(double length, int base, int extra) {

    public int segments() {
        return base + extra;
    }

    public double initialStep() {
        return length / segments();
    }

    /**
     * The layout of span {@code p0}→{@code p1}, or empty when it is shorter than half the sampling spacing.
     * {@code start}/{@code end} are the chain-end conditions that fall on this span; pass {@link EndCondition#FREE}
     * for the chain's inner nodes.
     */
    public static Optional<SpanLayout> of(Vec3d p0, Vec3d p1, double spacing, EndCondition start, EndCondition end) {
        Vec3d delta = p1.sub(p0);
        double d = delta.length();
        if (!Double.isFinite(d) || d < CurveRules.MIN_SPAN_FACTOR * spacing) return Optional.empty();
        double a = d;
        if (start.fixed()) a += d - start.direction().dot(delta);
        if (end.fixed()) a += d - end.direction().dot(delta);
        int base = (int) Math.ceil(Math.max(1, Math.min(CurveRules.MAX_BASE_SEGMENTS, a / spacing)));
        int extra = (start.fixed() ? 1 : 0) + (end.fixed() ? 1 : 0);
        return Optional.of(new SpanLayout(d, base, extra));
    }
}
