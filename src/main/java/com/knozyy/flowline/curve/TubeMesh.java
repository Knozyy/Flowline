package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of a curved pipe as quads: the block pipe's 6x6 px glass duct (tools/gen/models.py) carried along the
 * solved points. Four 1 px corner rails 2-3 px from the middle, glass panels 2.5 px out (seen from both sides), a 1 px
 * band every fourth point, and at the ends a collar (inserting end on a block), an extract ring, or a glass cap (free
 * end). A joint is a soft glass node with a band at each mouth.
 */
public final class TubeMesh {
    private static final double PX = 1 / 16.0;
    private static final double RAIL_OUT = 3 * PX, RAIL_IN = 2 * PX, GLASS = 2.5 * PX, BAND = 3.5 * PX;
    private static final int BAND_EVERY = 4;
    private static final int SLICES = 8, STACKS = 6;

    public enum EndStyle { NONE, COLLAR, RING, CAP }

    private TubeMesh() {}

    /** A point's frame: tangent T, normal N square to it, binormal B = T x N. */
    private record Frame(Vec3d p, Vec3d t, Vec3d n, Vec3d b) {
        Vec3d at(double u, double w) {
            return p.addScaled(n, u).addScaled(b, w);
        }

        Frame moved(double along) {
            return new Frame(p.addScaled(t, along), t, n, b);
        }
    }

    /** Frames at the points: tangents and normals averaged over the segments on either side, so joins don't crack. */
    private static List<Frame> frames(List<Vec3d> points) {
        List<Vec3d> segmentNormals = Frames.normals(points);
        int last = points.size() - 1;
        List<Frame> frames = new ArrayList<>(points.size());
        for (int i = 0; i <= last; i++) {
            Vec3d before = i > 0 ? points.get(i).sub(points.get(i - 1)).normalize() : null;
            Vec3d after = i < last ? points.get(i + 1).sub(points.get(i)).normalize() : null;
            Vec3d t = before == null ? after : after == null ? before : before.add(after).normalize();
            Vec3d n = i == 0 ? segmentNormals.get(0) : i == last ? segmentNormals.get(last - 1)
                    : segmentNormals.get(i - 1).add(segmentNormals.get(i));
            n = n.addScaled(t, -n.dot(t)).normalize();
            frames.add(new Frame(points.get(i), t, n, t.cross(n)));
        }
        return frames;
    }

    /** The duct along {@code points}, with the given pieces at its first and last point. */
    public static List<MeshQuad> edge(List<Vec3d> points, EndStyle start, EndStyle end) {
        List<Frame> f = frames(points);
        List<MeshQuad> quads = new ArrayList<>();
        for (int i = 0; i + 1 < f.size(); i++) {
            Frame a = f.get(i), b = f.get(i + 1);
            for (int cu = -1; cu <= 1; cu += 2) {
                for (int cw = -1; cw <= 1; cw += 2) {
                    double u0 = cu < 0 ? -RAIL_OUT : RAIL_IN, u1 = cu < 0 ? -RAIL_IN : RAIL_OUT;
                    double w0 = cw < 0 ? -RAIL_OUT : RAIL_IN, w1 = cw < 0 ? -RAIL_IN : RAIL_OUT;
                    rail(quads, a, b, u0, u1, w0, w1);
                }
            }
            panels(quads, MeshPart.GLASS, a, b, GLASS, false, 5.5f, 10.5f);
            panels(quads, MeshPart.GLASS_INNER, a, b, GLASS, true, 5.5f, 10.5f);
            if (i > 0 && i % BAND_EVERY == 0) band(quads, a);
        }
        endPiece(quads, f.get(0), 1, start);
        endPiece(quads, f.get(f.size() - 1), -1, end);
        return quads;
    }

    /** The four long faces of a rail from frame {@code a} to {@code b} spanning u0..u1 x w0..w1, facing out. */
    private static void rail(List<MeshQuad> q, Frame a, Frame b, double u0, double u1, double w0, double w1) {
        MeshPart r = MeshPart.RAIL;
        q.add(MeshQuad.of(r, a.at(u1, w0), a.at(u1, w1), b.at(u1, w1), b.at(u1, w0), 0, 0, 1, 16));
        q.add(MeshQuad.of(r, a.at(u0, w1), a.at(u0, w0), b.at(u0, w0), b.at(u0, w1), 0, 0, 1, 16));
        q.add(MeshQuad.of(r, a.at(u0, w1), b.at(u0, w1), b.at(u1, w1), a.at(u1, w1), 0, 0, 1, 16));
        q.add(MeshQuad.of(r, a.at(u1, w0), b.at(u1, w0), b.at(u0, w0), a.at(u0, w0), 0, 0, 1, 16));
    }

    /** Four panels at half width {@code h} from {@code a} to {@code b}; facing out, or in when {@code inward}. */
    private static void panels(List<MeshQuad> q, MeshPart part, Frame a, Frame b, double h, boolean inward,
                               float t0, float t1) {
        MeshQuad[] sides = {
                MeshQuad.of(part, a.at(h, -h), a.at(h, h), b.at(h, h), b.at(h, -h), t0, 0, t1, 16),
                MeshQuad.of(part, a.at(-h, h), a.at(-h, -h), b.at(-h, -h), b.at(-h, h), t0, 0, t1, 16),
                MeshQuad.of(part, a.at(-h, h), b.at(-h, h), b.at(h, h), a.at(h, h), t0, 0, t1, 16),
                MeshQuad.of(part, a.at(h, -h), b.at(h, -h), b.at(-h, -h), a.at(-h, -h), t0, 0, t1, 16)};
        for (MeshQuad side : sides) q.add(inward ? side.flipped() : side);
    }

    /** A 1 px band of the band's half width centred on {@code at}. */
    private static void band(List<MeshQuad> q, Frame at) {
        panels(q, MeshPart.RING, at.moved(-PX / 2), at.moved(PX / 2), BAND, false, 4.5f, 11.5f);
    }

    /** The piece at an end; {@code into} is +1 when the duct leaves the end along T, -1 when it arrives along T. */
    private static void endPiece(List<MeshQuad> q, Frame end, int into, EndStyle style) {
        switch (style) {
            case NONE -> {}
            case CAP -> q.add(lid(MeshPart.GLASS, end, GLASS, -into, 5.5f, 10.5f));
            case COLLAR -> collar(q, end, into, MeshPart.COLLAR, 4 * PX, 2 * PX);
            case RING -> collar(q, end, into, MeshPart.RING, 5 * PX, 2.5 * PX);
        }
    }

    /** A collar of half width {@code h} reaching {@code depth} from the end into the duct, closed by its front. */
    private static void collar(List<MeshQuad> q, Frame end, int into, MeshPart part, double h, double depth) {
        Frame front = end.moved(depth * into);
        panels(q, part, into > 0 ? end : front, into > 0 ? front : end, h, false, 8 - (float) (h / PX), 8 + (float) (h / PX));
        q.add(lid(part, front, h, into, 8 - (float) (h / PX), 8 + (float) (h / PX)));
    }

    /** A square of half width {@code h} on {@code at}, facing along T times {@code facing}. */
    private static MeshQuad lid(MeshPart part, Frame at, double h, int facing, float t0, float t1) {
        MeshQuad lid = MeshQuad.of(part, at.at(-h, -h), at.at(-h, h), at.at(h, h), at.at(h, -h), t0, t0, t1, t1);
        return lid.normal().dot(at.t()) * facing < 0 ? lid.flipped() : lid;
    }

    /** A soft glass node of {@code radius} at {@code centre}, with a band at each exit direction. */
    public static List<MeshQuad> joint(Vec3d centre, double radius, List<Vec3d> exits) {
        List<MeshQuad> q = new ArrayList<>();
        for (int s = 0; s < STACKS; s++) {
            double t0 = Math.PI * s / STACKS, t1 = Math.PI * (s + 1) / STACKS;
            for (int k = 0; k < SLICES; k++) {
                double p0 = 2 * Math.PI * k / SLICES, p1 = 2 * Math.PI * (k + 1) / SLICES;
                q.add(MeshQuad.of(MeshPart.NODE_GLASS, sphere(centre, radius, t0, p0), sphere(centre, radius, t0, p1),
                        sphere(centre, radius, t1, p1), sphere(centre, radius, t1, p0),
                        16f * k / SLICES, 16f * s / STACKS, 16f * (k + 1) / SLICES, 16f * (s + 1) / STACKS));
            }
        }
        for (Vec3d exit : exits) {
            Vec3d t = exit.normalize();
            Vec3d n = Frames.blockNormal(t);
            band(q, new Frame(centre.addScaled(t, radius), t, n, t.cross(n)));
        }
        return q;
    }

    private static Vec3d sphere(Vec3d c, double r, double theta, double phi) {
        return new Vec3d(c.x() + r * Math.sin(theta) * Math.cos(phi), c.y() + r * Math.cos(theta),
                c.z() + r * Math.sin(theta) * Math.sin(phi));
    }
}
