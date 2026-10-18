package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.Obb;
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
}
