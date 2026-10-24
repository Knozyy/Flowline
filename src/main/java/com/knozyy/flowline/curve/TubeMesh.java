package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of a curved pipe as quads: the block pipe's 6x6 px glass duct (tools/gen/models.py) carried along the
 * solved points. Four 1 px corner rails 2-3 px from the middle, glass panels 2.5 px out (seen from both sides), a 1 px
 * band every fourth point, and at the ends a collar (inserting end on a block), an extract ring, or a glass cap (free
 * end). A joint is a square glass box on the world axes with a 1 px metal frame on its twelve edges; the ducts run
 * into it.
 */
public final class TubeMesh {
    private static final double PX = 1 / 16.0;
    private static final double RAIL_OUT = 3 * PX, RAIL_IN = 2 * PX, GLASS = 2.5 * PX, BAND = 3.5 * PX;
    private static final int BAND_EVERY = 4;

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

    /**
     * Frames at the points: tangents and normals averaged over the segments on either side, so joins don't crack. An
     * end given a {@code startTangent}/{@code endTangent} (where the edge meets the next one through a node) uses that
     * direction and the square section of {@link #section}, which the neighbour uses too.
     */
    private static List<Frame> frames(List<Vec3d> points, Vec3d startTangent, Vec3d endTangent) {
        List<Vec3d> segmentNormals = Frames.normals(points);
        int last = points.size() - 1;
        List<Frame> frames = new ArrayList<>(points.size());
        for (int i = 0; i <= last; i++) {
            Vec3d before = i > 0 ? points.get(i).sub(points.get(i - 1)).normalize() : null;
            Vec3d after = i < last ? points.get(i + 1).sub(points.get(i)).normalize() : null;
            Vec3d t = before == null ? after : after == null ? before : before.add(after).normalize();
            Vec3d n = i == 0 ? segmentNormals.get(0) : i == last ? segmentNormals.get(last - 1)
                    : segmentNormals.get(i - 1).add(segmentNormals.get(i));
            Vec3d shared = i == 0 ? startTangent : i == last ? endTangent : null;
            if (shared != null) {
                t = shared;
                n = section(t, n);
            } else {
                n = n.addScaled(t, -n.dot(t)).normalize();
            }
            frames.add(new Frame(points.get(i), t, n, t.cross(n)));
        }
        return frames;
    }

    /** The duct along {@code points}, with the given pieces at its first and last point. */
    public static List<MeshQuad> edge(List<Vec3d> points, EndStyle start, EndStyle end) {
        return edge(points, start, end, null, null);
    }

    /**
     * The duct, its first and/or last cross-section square to the given unit direction instead of its own end segment
     * (null keeps its own): two edges meeting at a node pass the same direction, so their sections coincide.
     */
    public static List<MeshQuad> edge(List<Vec3d> points, EndStyle start, EndStyle end, Vec3d startTangent,
                                      Vec3d endTangent) {
        List<Frame> f = frames(points, startTangent, endTangent);
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

    /**
     * The grid-aligned square section square to {@code t}, turned by the quarter turn closest to {@code near}: every
     * quarter turn is the same square, so neighbours agree on it, and {@code near} keeps the rails from crossing over.
     */
    static Vec3d section(Vec3d t, Vec3d near) {
        Vec3d n = Frames.blockNormal(t), b = t.cross(n), best = n;
        for (Vec3d c : new Vec3d[]{b, n.scale(-1), b.scale(-1)}) if (c.dot(near) > best.dot(near)) best = c;
        return best;
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

    /**
     * A square glass box of half width {@code half} at {@code centre}, on the world axes like a block pipe's middle,
     * framed by twelve 1 px metal bars along its edges. The ducts need nothing more: they end at the centre, inside
     * the box.
     */
    public static List<MeshQuad> joint(Vec3d centre, double half) {
        List<MeshQuad> q = new ArrayList<>();
        double g = half - PX / 2;
        box(q, MeshPart.GLASS, centre, new double[]{-g, -g, -g}, new double[]{g, g, g}, false, 2, 14);
        box(q, MeshPart.GLASS_INNER, centre, new double[]{-g, -g, -g}, new double[]{g, g, g}, true, 2, 14);
        for (int axis = 0; axis < 3; axis++) {
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    double[] lo = new double[3], hi = new double[3];
                    lo[axis] = -half;
                    hi[axis] = half;
                    int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
                    lo[a1] = s1 < 0 ? -half : half - PX;
                    hi[a1] = s1 < 0 ? -half + PX : half;
                    lo[a2] = s2 < 0 ? -half : half - PX;
                    hi[a2] = s2 < 0 ? -half + PX : half;
                    box(q, MeshPart.JOINT, centre, lo, hi, false, 0, 1);
                }
            }
        }
        return q;
    }

    /** The six faces of the world-aligned box {@code centre + lo..hi}, facing out, or in when {@code inward}. */
    private static void box(List<MeshQuad> q, MeshPart part, Vec3d centre, double[] lo, double[] hi, boolean inward,
                            float t0, float t1) {
        for (int axis = 0; axis < 3; axis++) {
            int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
            for (int side = -1; side <= 1; side += 2) {
                double[][] corners = new double[4][3];
                double[][] uv = {{lo[a1], lo[a2]}, {hi[a1], lo[a2]}, {hi[a1], hi[a2]}, {lo[a1], hi[a2]}};
                for (int k = 0; k < 4; k++) {
                    corners[k][axis] = side < 0 ? lo[axis] : hi[axis];
                    corners[k][a1] = uv[k][0];
                    corners[k][a2] = uv[k][1];
                }
                MeshQuad face = MeshQuad.of(part, at(centre, corners[0]), at(centre, corners[1]), at(centre, corners[2]),
                        at(centre, corners[3]), t0, t0, t1, t1);
                double[] out = new double[3];
                out[axis] = side * (inward ? -1 : 1);
                if (face.normal().dot(new Vec3d(out[0], out[1], out[2])) < 0) face = face.flipped();
                q.add(face);
            }
        }
    }

    private static Vec3d at(Vec3d c, double[] d) {
        return new Vec3d(c.x() + d[0], c.y() + d[1], c.z() + d[2]);
    }
}
