package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.SpanLayout;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** The curve solver, step by step: span layout, the KKT system, the linear solver and the Newton loop. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveSolverGameTests {
    private static final EndCondition FREE = EndCondition.FREE;

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    private static EndCondition dir(double x, double y, double z) {
        return new EndCondition(v(x, y, z));
    }

    // ---- span layout -------------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spanLayoutStraightFreeEnds(GameTestHelper h) {
        SpanLayout s = SpanLayout.of(v(0, 0, 0), v(4, 0, 0), 0.5, FREE, FREE).orElseThrow();
        h.assertTrue(s.base() == 8 && s.extra() == 0 && s.segments() == 8, "8 free segments, got " + s);
        h.assertTrue(near(s.initialStep(), 0.5, 1e-12), "step 0.5, got " + s.initialStep());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spanLayoutRejectsShortSpan(GameTestHelper h) {
        h.assertTrue(SpanLayout.of(v(0, 0, 0), v(0.2, 0, 0), 0.5, FREE, FREE).isEmpty(), "d < 0.5·s is rejected");
        h.assertTrue(SpanLayout.of(v(0, 0, 0), v(0.25, 0, 0), 0.5, FREE, FREE).isPresent(), "d = 0.5·s is allowed");
        h.assertTrue(SpanLayout.of(v(1, 1, 1), v(1, 1, 1), 0.5, FREE, FREE).isEmpty(), "coincident nodes are rejected");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spanLayoutBothEndsFixed(GameTestHelper h) {
        SpanLayout s = SpanLayout.of(v(0, 0, 0), v(2, 2, 0), 0.5, dir(0, 1, 0), dir(1, 0, 0)).orElseThrow();
        h.assertTrue(s.base() == 9 && s.extra() == 2 && s.segments() == 11, "9 + 2 segments, got " + s);
        h.assertTrue(near(s.initialStep(), 2 * Math.sqrt(2) / 11, 1e-12), "step d/11, got " + s.initialStep());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spanLayoutClamps(GameTestHelper h) {
        SpanLayout longSpan = SpanLayout.of(v(0, 0, 0), v(100, 0, 0), 0.375, FREE, FREE).orElseThrow();
        h.assertTrue(longSpan.base() == 64, "at most 64 base segments, got " + longSpan);
        SpanLayout shortSpan = SpanLayout.of(v(0, 0, 0), v(0.3, 0, 0), 0.375, dir(1, 0, 0), dir(1, 0, 0)).orElseThrow();
        h.assertTrue(shortSpan.base() == 1 && shortSpan.extra() == 2, "at least one base segment, got " + shortSpan);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placeErrorCodesMatchCurvy(GameTestHelper h) {
        PlaceError[] order = {PlaceError.INVALID_SHAPE, PlaceError.INVALID_OBJECT, PlaceError.TOO_COSTLY,
                PlaceError.TOO_FAR, PlaceError.INTERSECT_BLOCK, PlaceError.INTERSECT_PIPE, PlaceError.ANGLE_TOO_SMALL,
                PlaceError.NODE_IN_JOINT};
        for (int i = 0; i < order.length; i++) {
            h.assertTrue(order[i].code() == i + 1, order[i] + " has Curvy's code " + (i + 1));
            h.assertTrue(PlaceError.byCode(i + 1).equals(Optional.of(order[i])), "code " + (i + 1) + " reads back");
        }
        h.assertTrue(PlaceError.byCode(9).isEmpty() && PlaceError.byCode(0).isEmpty(), "unknown codes are empty");
        h.assertTrue(PlaceError.TOO_FAR.key().equals("message.flowline.curve.too_far"), "lang key");
        h.succeed();
    }
}
