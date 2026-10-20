package com.knozyy.flowline.curve;

/** Axis-aligned box in world coordinates. */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public static Aabb of(Vec3d... points) {
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        for (Vec3d p : points) {
            x0 = Math.min(x0, p.x());
            y0 = Math.min(y0, p.y());
            z0 = Math.min(z0, p.z());
            x1 = Math.max(x1, p.x());
            y1 = Math.max(y1, p.y());
            z1 = Math.max(z1, p.z());
        }
        return new Aabb(x0, y0, z0, x1, y1, z1);
    }

    public Aabb inflate(double r) {
        return new Aabb(minX - r, minY - r, minZ - r, maxX + r, maxY + r, maxZ + r);
    }

    public Aabb union(Aabb o) {
        return new Aabb(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }

    /** Closed-interval test, for broad-phase culling; touching boxes intersect. */
    public boolean intersects(Aabb o) {
        return minX <= o.maxX && maxX >= o.minX
                && minY <= o.maxY && maxY >= o.minY
                && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public boolean contains(Vec3d p) {
        return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ;
    }

    public Vec3d center() {
        return new Vec3d((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }
}
