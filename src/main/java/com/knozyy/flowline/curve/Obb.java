package com.knozyy.flowline.curve;

/**
 * Oriented box: one segment of a square curved pipe. {@code u} runs along the segment, {@code v} and {@code w} span the
 * square cross-section; all three are unit vectors at right angles.
 */
public record Obb(Vec3d center, Vec3d u, Vec3d v, Vec3d w, double hu, double hv, double hw) {
    private static final Vec3d[] WORLD = {new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1)};

    /** The box around segment {@code a}→{@code b} with a square section, {@code normal} giving its rotation. */
    public static Obb segment(Vec3d a, Vec3d b, Vec3d normal, double halfWidth) {
        Vec3d axis = b.sub(a);
        double length = axis.length();
        Vec3d u = axis.scale(1 / length);
        Vec3d v = normal.addScaled(u, -normal.dot(u)).normalize();
        return new Obb(a.add(b).scale(0.5), u, v, u.cross(v), length / 2, halfWidth, halfWidth);
    }

    public Aabb bounds() {
        double ex = extent(WORLD[0]), ey = extent(WORLD[1]), ez = extent(WORLD[2]);
        return new Aabb(center.x() - ex, center.y() - ey, center.z() - ez, center.x() + ex, center.y() + ey,
                center.z() + ez);
    }

    /** Half the length of this box's shadow on unit axis {@code l}. */
    private double extent(Vec3d l) {
        return hu * Math.abs(u.dot(l)) + hv * Math.abs(v.dot(l)) + hw * Math.abs(w.dot(l));
    }

    public boolean overlaps(Aabb box) {
        Vec3d c = box.center();
        Vec3d half = new Vec3d((box.maxX() - box.minX()) / 2, (box.maxY() - box.minY()) / 2, (box.maxZ() - box.minZ()) / 2);
        return overlaps(new Obb(c, WORLD[0], WORLD[1], WORLD[2], half.x(), half.y(), half.z()));
    }

    /** Separating axis test over the 15 candidate axes; see {@link CurveRules#OVERLAP_EPS}. */
    public boolean overlaps(Obb o) {
        Vec3d[] mine = {u, v, w};
        Vec3d[] theirs = {o.u, o.v, o.w};
        Vec3d between = o.center.sub(center);
        for (Vec3d axis : mine) if (separated(o, between, axis)) return false;
        for (Vec3d axis : theirs) if (separated(o, between, axis)) return false;
        for (Vec3d a : mine) {
            for (Vec3d b : theirs) {
                Vec3d axis = a.cross(b);
                double length = axis.length();
                if (length < 1e-12) continue;
                if (separated(o, between, axis.scale(1 / length))) return false;
            }
        }
        return true;
    }

    private boolean separated(Obb o, Vec3d between, Vec3d axis) {
        double overlap = extent(axis) + o.extent(axis) - Math.abs(between.dot(axis));
        return overlap <= CurveRules.OVERLAP_EPS;
    }

    /**
     * Distance along the unit ray to where it enters this box, 0 when it starts inside, or NaN when it misses within
     * {@code maxDist}.
     */
    public double rayHit(Vec3d origin, Vec3d dir, double maxDist) {
        Vec3d rel = origin.sub(center);
        Vec3d[] axes = {u, v, w};
        double[] halves = {hu, hv, hw};
        double near = 0, far = maxDist;
        for (int i = 0; i < 3; i++) {
            double o = rel.dot(axes[i]), d = dir.dot(axes[i]);
            if (Math.abs(d) < 1e-12) {
                if (Math.abs(o) > halves[i]) return Double.NaN;
                continue;
            }
            double t1 = (-halves[i] - o) / d, t2 = (halves[i] - o) / d;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return Double.NaN;
        }
        return near;
    }
}
