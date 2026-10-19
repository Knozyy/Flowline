package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveVoxels;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EdgeShape;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.Frames;
import com.knozyy.flowline.curve.Obb;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The curve geometry core: boxes, frames and edge shapes. Pure arithmetic, no world needed. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveGeometryGameTests {
    private static final double R = 0.1875;
    private static final Aabb CUBE = new Aabb(0, 0, 0, 1, 1, 1);

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obbAabbTouchingFaceIsNotOverlap(GameTestHelper h) {
        Obb resting = Obb.segment(v(0, 1.1875, 0.5), v(1, 1.1875, 0.5), v(0, 1, 0), R);
        h.assertTrue(!resting.overlaps(CUBE), "a pipe resting on a block only touches it");
        Obb sunk = Obb.segment(v(0, 1.1775, 0.5), v(1, 1.1775, 0.5), v(0, 1, 0), R);
        h.assertTrue(sunk.overlaps(CUBE), "a pipe 0.01 into the block overlaps it");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obbSeparatesDiagonalCorner(GameTestHelper h) {
        Obb thin = Obb.segment(v(0.9, 1.5, 0.5), v(1.5, 0.9, 0.5), v(0, 0, 1), R);
        h.assertTrue(thin.bounds().intersects(CUBE), "the bounds overlap, so only the exact test can separate them");
        h.assertTrue(!thin.overlaps(CUBE), "the corner is 0.283 away, more than the half width");
        Obb wide = Obb.segment(v(0.9, 1.5, 0.5), v(1.5, 0.9, 0.5), v(0, 0, 1), 0.3);
        h.assertTrue(wide.overlaps(CUBE), "a 0.3 half width reaches the corner");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obbObbParallelNeighbours(GameTestHelper h) {
        Obb a = Obb.segment(v(0, 0, 0), v(2, 0, 0), v(0, 1, 0), R);
        Obb touching = Obb.segment(v(0, 0.375, 0), v(2, 0.375, 0), v(0, 1, 0), R);
        Obb into = Obb.segment(v(0, 0.37, 0), v(2, 0.37, 0), v(0, 1, 0), R);
        h.assertTrue(!a.overlaps(touching) && !touching.overlaps(a), "pipes side by side only touch");
        h.assertTrue(a.overlaps(into) && into.overlaps(a), "pipes 0.005 into each other overlap");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obbRayHitsFromOutsideAndInside(GameTestHelper h) {
        Obb box = Obb.segment(v(0, 1, 0.5), v(1, 1, 0.5), v(0, 1, 0), R);
        double down = box.rayHit(v(0.5, 5, 0.5), v(0, -1, 0), 10);
        h.assertTrue(near(down, 3.8125, 1e-9), "a ray from above hits the top face, got " + down);
        h.assertTrue(Double.isNaN(box.rayHit(v(0.5, 5, 0.5), v(0, 1, 0), 10)), "a ray going away misses");
        h.assertTrue(box.rayHit(v(0.5, 1, 0.5), v(0, -1, 0), 10) == 0, "a ray starting inside hits at once");
        h.succeed();
    }

    // ---- frames and edge shapes --------------------------------------------------------------------------

    private static List<Vec3d> straight(Vec3d step, int segments) {
        List<Vec3d> points = new java.util.ArrayList<>();
        for (int i = 0; i <= segments; i++) points.add(step.scale(i));
        return points;
    }

    private static SolvedSpan elbow() {
        return CurveSolver.solve(List.of(v(0, 0, 0), v(2, 2, 0)), CurveRules.DIAMETER, new EndCondition(v(0, 1, 0)),
                new EndCondition(v(1, 0, 0))).orElseThrow().spans().get(0);
    }

    private static Vec3d tangent(List<Vec3d> points, int segment) {
        return points.get(segment + 1).sub(points.get(segment)).normalize();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void framesStraightAlongXUseUp(GameTestHelper h) {
        for (Vec3d n : Frames.normals(straight(v(0.5, 0, 0), 8))) {
            h.assertTrue(n.distanceSq(v(0, 1, 0)) < 1e-24, "a pipe along x keeps its top up, got " + n);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void framesVerticalUseZ(GameTestHelper h) {
        for (Vec3d n : Frames.normals(straight(v(0, 0.5, 0), 8))) {
            h.assertTrue(n.distanceSq(v(0, 0, 1)) < 1e-24, "a vertical pipe faces z, got " + n);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void framesStayPerpendicular(GameTestHelper h) {
        List<Vec3d> points = elbow().points();
        List<Vec3d> normals = Frames.normals(points);
        h.assertTrue(normals.size() == points.size() - 1, "one normal per segment");
        for (int k = 0; k < normals.size(); k++) {
            h.assertTrue(near(normals.get(k).length(), 1, 1e-9), "unit normal " + k);
            h.assertTrue(Math.abs(normals.get(k).dot(tangent(points, k))) < 1e-9, "normal " + k + " is square to its segment");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void framesEndsMatchBlockAxes(GameTestHelper h) {
        endsMatchBlockAxes(h, elbow().points());
        // a chain that leaves its plane, so parallel transport alone ends up turned off the grid
        List<Vec3d> twisted = new java.util.ArrayList<>();
        for (SolvedSpan span : CurveSolver.solve(List.of(v(0, 0, 0), v(2, 1, 1), v(3, 3, 0), v(4, 3, 3)),
                CurveRules.DIAMETER, EndCondition.FREE, EndCondition.FREE).orElseThrow().spans()) {
            if (!twisted.isEmpty()) twisted.remove(twisted.size() - 1);
            twisted.addAll(span.points());
        }
        endsMatchBlockAxes(h, twisted);
        h.succeed();
    }

    private static void endsMatchBlockAxes(GameTestHelper h, List<Vec3d> points) {
        List<Vec3d> normals = Frames.normals(points);
        Vec3d first = Frames.blockNormal(tangent(points, 0));
        h.assertTrue(normals.get(0).distanceSq(first) < 1e-18, "the first segment is square to the block grid");
        int last = normals.size() - 1;
        Vec3d t = tangent(points, last), target = Frames.blockNormal(t);
        Vec3d n = normals.get(last);
        boolean aligned = Math.abs(n.dot(target)) > 1 - 1e-9 || Math.abs(n.dot(t.cross(target))) > 1 - 1e-9;
        h.assertTrue(aligned, "the last segment is square to the block grid up to a quarter turn, got " + n);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void edgeBoxesCoverSegments(GameTestHelper h) {
        List<Obb> boxes = EdgeShape.boxes(new SolvedSpan(straight(v(0.5, 0, 0), 8), 0.5), R);
        h.assertTrue(boxes.size() == 8, "one box per segment");
        Aabb b = EdgeShape.bounds(boxes);
        h.assertTrue(near(b.minX(), 0, 1e-9) && near(b.maxX(), 4, 1e-9) && near(b.minY(), -R, 1e-9)
                && near(b.maxY(), R, 1e-9) && near(b.minZ(), -R, 1e-9) && near(b.maxZ(), R, 1e-9), "bounds " + b);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curvePackageHasNoMinecraftTypes(GameTestHelper h) {
        Path dir = ModList.get().getModFileById(Flowline.MODID).getFile().findResource("com", "knozyy", "flowline", "curve");
        List<Path> classes;
        try (Stream<Path> files = Files.walk(dir)) {
            classes = files.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + dir, e);
        }
        h.assertTrue(!classes.isEmpty(), "the curve package must be found at " + dir);
        for (Path file : classes) {
            String bytes;
            try {
                bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + file, e);
            }
            for (String banned : List.of("net/minecraft", "com/mojang", "net/minecraftforge")) {
                h.assertTrue(!bytes.contains(banned), file.getFileName() + " refers to " + banned);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obbVoxelsCoverTheBox(GameTestHelper h) {
        double px = 1 / 16.0;
        for (Obb box : List.of(Obb.segment(v(0, 0, 0), v(0.4, 0.3, 0.2), v(0, 1, 0), 3 * px),
                Obb.segment(v(0, 0, 0), v(0.5, 0, 0), v(0, 1, 0), 3 * px))) {
            List<Aabb> boxes = CurveVoxels.boxes(box, px / 2);
            h.assertTrue(boxes.size() <= 200, "a handful of boxes per segment, got " + boxes.size());
            for (double a = -0.95; a <= 0.95; a += 0.19) {
                for (double b = -0.95; b <= 0.95; b += 0.19) {
                    for (double c = -0.95; c <= 0.95; c += 0.19) {
                        Vec3d p = box.center().addScaled(box.u(), a * box.hu()).addScaled(box.v(), b * box.hv())
                                .addScaled(box.w(), c * box.hw());
                        h.assertTrue(boxes.stream().anyMatch(x -> x.contains(p)), "a point of the box is covered: " + p);
                    }
                }
            }
            for (Aabb x : boxes) {
                for (int i = 0; i < 8; i++) {
                    Vec3d corner = new Vec3d((i & 1) == 0 ? x.minX() : x.maxX(), (i & 2) == 0 ? x.minY() : x.maxY(),
                            (i & 4) == 0 ? x.minZ() : x.maxZ());
                    Vec3d d = corner.sub(box.center());
                    double out = 2 * px + 1e-9;
                    h.assertTrue(Math.abs(d.dot(box.u())) <= box.hu() + out && Math.abs(d.dot(box.v())) <= box.hv() + out
                            && Math.abs(d.dot(box.w())) <= box.hw() + out, "a box corner stays within two pixels: " + corner);
                }
            }
        }
        h.assertTrue(CurveVoxels.boxes(Obb.segment(v(0, 0, 0), v(0.5, 0, 0), v(0, 1, 0), 3 * px), px / 2).size() == 16,
                "a straight run along x needs no cut across");
        h.succeed();
    }
}
