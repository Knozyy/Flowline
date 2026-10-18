package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * Which way the square section of each segment faces. Flowline's choice: both ends sit square to the block grid, like
 * the block pipes they meet, and the twist between them is spread evenly along the pipe. Collision boxes and the mesh
 * use the same normals, so the square that is seen is the square that collides.
 */
public final class Frames {
    private static final double QUARTER = Math.PI / 2;

    private Frames() {}

    /** The grid-aligned "up" of a segment running along {@code tangent}: +Y, or +Z when it runs (nearly) vertically. */
    public static Vec3d blockNormal(Vec3d tangent) {
        Vec3d reference = Math.abs(tangent.y()) > 0.9 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
        return square(reference, tangent);
    }

    /** One unit normal per segment of {@code points}, square to its segment. */
    public static List<Vec3d> normals(List<Vec3d> points) {
        int segments = points.size() - 1;
        List<Vec3d> tangents = new ArrayList<>(segments);
        for (int k = 0; k < segments; k++) tangents.add(points.get(k + 1).sub(points.get(k)).normalize());
        List<Vec3d> normals = new ArrayList<>(segments);
        normals.add(blockNormal(tangents.get(0)));
        for (int k = 1; k < segments; k++) {
            Vec3d from = tangents.get(k - 1), to = tangents.get(k);
            Vec3d axis = from.cross(to);
            double sin = axis.length();
            Vec3d n = normals.get(k - 1);
            if (sin > 1e-12) n = rotate(n, axis.scale(1 / sin), Math.atan2(sin, from.dot(to)));
            normals.add(square(n, to));
        }
        int last = segments - 1;
        Vec3d end = tangents.get(last), target = blockNormal(end), n = normals.get(last);
        double twist = Math.atan2(n.cross(target).dot(end), n.dot(target));
        twist -= QUARTER * Math.rint(twist / QUARTER);
        if (segments > 1 && twist != 0) {
            for (int k = 1; k < segments; k++) {
                Vec3d t = tangents.get(k);
                normals.set(k, square(rotate(normals.get(k), t, twist * k / last), t));
            }
        }
        return normals;
    }

    /** {@code v} made square to unit {@code t} and unit length. */
    private static Vec3d square(Vec3d v, Vec3d t) {
        return v.addScaled(t, -v.dot(t)).normalize();
    }

    /** {@code v} turned by {@code angle} about unit {@code axis} (Rodrigues). */
    private static Vec3d rotate(Vec3d v, Vec3d axis, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).addScaled(axis, axis.dot(v) * (1 - cos));
    }
}
