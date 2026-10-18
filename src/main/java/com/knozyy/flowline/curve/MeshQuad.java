package com.knozyy.flowline.curve;

/**
 * One four-cornered face of a curved pipe's mesh, in world coordinates. The corners run anticlockwise seen from the side
 * {@code normal} points to; {@code u0..v1} are texture pixels (0-16).
 */
public record MeshQuad(MeshPart part, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal, float u0, float v0, float u1, float v1) {
    /** A quad with its normal from the diagonals, which also holds when two corners meet (a sphere's poles). */
    static MeshQuad of(MeshPart part, Vec3d a, Vec3d b, Vec3d c, Vec3d d, float u0, float v0, float u1, float v1) {
        Vec3d n = c.sub(a).cross(d.sub(b));
        return new MeshQuad(part, a, b, c, d, n.normalize(), u0, v0, u1, v1);
    }

    MeshQuad flipped() {
        return of(part, d, c, b, a, u0, v0, u1, v1);
    }
}
