package com.knozyy.flowline.curve;

/**
 * Immutable double-precision 3D vector. The curve package avoids Minecraft types so its geometry can be checked
 * without the game.
 */
public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    public Vec3d add(Vec3d o) {
        return new Vec3d(x + o.x, y + o.y, z + o.z);
    }

    public Vec3d sub(Vec3d o) {
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    public Vec3d scale(double s) {
        return new Vec3d(x * s, y * s, z * s);
    }

    public Vec3d addScaled(Vec3d o, double s) {
        return new Vec3d(x + o.x * s, y + o.y * s, z + o.z * s);
    }

    public double dot(Vec3d o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3d cross(Vec3d o) {
        return new Vec3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSq() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double distanceSq(Vec3d o) {
        return sub(o).lengthSq();
    }

    /** Unit vector in the same direction, or {@link #ZERO} for a (near) zero vector. */
    public Vec3d normalize() {
        double l = length();
        return l < 1e-12 ? ZERO : scale(1 / l);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
