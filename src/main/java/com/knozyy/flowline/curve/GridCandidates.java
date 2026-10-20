package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The points a grid mode offers in a block cell, the ones blocks leave free, and the one the player aims at
 * (curvy-behaviour-addendum-re.md, B6): candidates inside a block's outline grown by 0.126 are dropped, and the pick is
 * the candidate nearest to the crosshair on screen, not in the world.
 */
public final class GridCandidates {
    /** How far outlines are grown before candidates inside them are dropped. */
    public static final double OUTLINE_MARGIN = 0.126;

    private GridCandidates() {}

    public static List<Vec3d> of(GridMode mode, int x, int y, int z) {
        Vec3d c = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
        Set<Vec3d> out = new LinkedHashSet<>();
        switch (mode) {
            case OFF -> {
                return List.of();
            }
            case AXIAL -> {
                out.add(c);
                for (int axis = 0; axis < 3; axis++) {
                    for (double d : new double[]{-0.5, -0.25, 0.25, 0.5}) out.add(c.add(along(axis, d)));
                }
            }
            case CORNER_P -> {
                for (int big = 0; big < 3; big++) {
                    for (double a : new double[]{-0.25, 0.25}) {
                        for (double b : new double[]{-0.25, 0.25}) {
                            for (double e : new double[]{-0.5, 0.5}) out.add(c.add(place(big, e, a, b)));
                        }
                    }
                }
            }
            case CORNER_N -> {
                for (double a : new double[]{-0.25, 0.25}) {
                    for (double b : new double[]{-0.25, 0.25}) {
                        for (double e : new double[]{-0.25, 0.25}) out.add(c.add(new Vec3d(a, b, e)));
                    }
                }
            }
            case EDGE_P -> {
                for (int zero = 0; zero < 3; zero++) {
                    for (int half = 0; half < 3; half++) {
                        if (half == zero) continue;
                        int quarter = 3 - zero - half;
                        for (double q : new double[]{-0.25, 0.25}) {
                            for (double hv : new double[]{-0.5, 0.5}) out.add(c.add(axes(zero, 0, half, hv, quarter, q)));
                        }
                    }
                }
            }
            case EDGE_N -> {
                for (int zero = 0; zero < 3; zero++) {
                    int a = (zero + 1) % 3, b = (zero + 2) % 3;
                    for (double p : new double[]{-0.25, 0.25}) {
                        for (double q : new double[]{-0.25, 0.25}) out.add(c.add(axes(zero, 0, a, p, b, q)));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static List<Vec3d> free(List<Vec3d> candidates, List<Aabb> outlines) {
        List<Vec3d> free = new ArrayList<>();
        for (Vec3d p : candidates) {
            boolean inside = false;
            for (Aabb box : outlines) inside |= box.inflate(OUTLINE_MARGIN).contains(p);
            if (!inside) free.add(p);
        }
        return free;
    }

    public static Optional<Vec3d> pick(List<Vec3d> candidates, Vec3d eye, Vec3d look) {
        Vec3d forward = look.normalize();
        Vec3d right = forward.cross(Math.abs(forward.y()) > 0.99 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.cross(forward);
        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vec3d p : candidates) {
            Vec3d rel = p.sub(eye);
            double depth = rel.dot(forward);
            if (depth <= 0) continue;
            double x = rel.dot(right) / depth, y = rel.dot(up) / depth;
            double score = x * x + y * y;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vec3d along(int axis, double d) {
        return axes(axis, d, (axis + 1) % 3, 0, (axis + 2) % 3, 0);
    }

    private static Vec3d place(int big, double e, double a, double b) {
        return axes(big, e, (big + 1) % 3, a, (big + 2) % 3, b);
    }

    /** A vector with value {@code va} on axis {@code a}, {@code vb} on {@code b}, {@code vc} on {@code c}. */
    private static Vec3d axes(int a, double va, int b, double vb, int c, double vc) {
        double[] v = new double[3];
        v[a] = va;
        v[b] = vb;
        v[c] = vc;
        return new Vec3d(v[0], v[1], v[2]);
    }
}
