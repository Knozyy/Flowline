package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/** The collision shape of a solved span: one square box per segment, faced by {@link Frames#normals}. */
public final class EdgeShape {
    private EdgeShape() {}

    public static List<Obb> boxes(SolvedSpan span, double halfWidth) {
        List<Vec3d> points = span.points();
        List<Vec3d> normals = Frames.normals(points);
        List<Obb> boxes = new ArrayList<>(normals.size());
        for (int k = 0; k < normals.size(); k++) {
            boxes.add(Obb.segment(points.get(k), points.get(k + 1), normals.get(k), halfWidth));
        }
        return boxes;
    }

    public static Aabb bounds(List<Obb> boxes) {
        Aabb all = boxes.get(0).bounds();
        for (int k = 1; k < boxes.size(); k++) all = all.union(boxes.get(k).bounds());
        return all;
    }
}
