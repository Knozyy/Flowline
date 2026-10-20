package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * A segment's square box as axis-aligned boxes, because entity collision only knows those: thin slabs along the
 * segment, and when the section is turned off the world axes, each slab cut into a 3x3 grid as well. The boxes cover
 * the segment's box; a section square to the world sticks out by nothing across, a tilted one by up to two pixels
 * (an approximation: finer cuts cost hundreds of boxes per segment on every entity move).
 */
public final class CurveVoxels {
    private static final double ALIGNED = 1 - 1e-9;
    private static final int CROSS = 3;

    private CurveVoxels() {}

    /** Boxes covering {@code box}, slabs at most {@code slice} thick along its length. */
    public static List<Aabb> boxes(Obb box, double slice) {
        int along = Math.max(1, (int) Math.ceil(2 * box.hu() / slice));
        int across = aligned(box.v()) && aligned(box.w()) ? 1 : CROSS;
        double su = box.hu() / along, sv = box.hv() / across, sw = box.hw() / across;
        List<Aabb> boxes = new ArrayList<>(along * across * across);
        for (int i = 0; i < along; i++) {
            double cu = -box.hu() + (2 * i + 1) * su;
            for (int j = 0; j < across; j++) {
                double cv = -box.hv() + (2 * j + 1) * sv;
                for (int k = 0; k < across; k++) {
                    double cw = -box.hw() + (2 * k + 1) * sw;
                    Vec3d c = box.center().addScaled(box.u(), cu).addScaled(box.v(), cv).addScaled(box.w(), cw);
                    boxes.add(new Obb(c, box.u(), box.v(), box.w(), su, sv, sw).bounds());
                }
            }
        }
        return boxes;
    }

    private static boolean aligned(Vec3d axis) {
        return Math.abs(axis.x()) > ALIGNED || Math.abs(axis.y()) > ALIGNED || Math.abs(axis.z()) > ALIGNED;
    }
}
