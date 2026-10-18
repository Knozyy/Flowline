package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.JointRules;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Curvy Pipes' joint, angle, reach and cost rules, and the placement validator built on them. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveRulesGameTests {
    private static final double D = 0.375, R = 0.1875, R2 = 0.1875 * Math.sqrt(2);

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    /** A unit vector in the x-y plane whose dot with (1,0,0) is {@code dot}. */
    private static Vec3d atDot(double dot) {
        return v(dot, Math.sqrt(1 - dot * dot), 0);
    }

    // ---- joints and angles -------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jointRadiusFollowsCurvy(GameTestHelper h) {
        h.assertTrue(near(JointRules.pipeRadius(D), R, 1e-12), "pipe radius is half the diameter");
        h.assertTrue(near(JointRules.jointRadius(new double[]{D}, false), R, 1e-12), "one edge");
        h.assertTrue(near(JointRules.jointRadius(new double[]{D, D}, false), R, 1e-12), "two edges");
        h.assertTrue(near(JointRules.jointRadius(new double[]{D, D, D}, false), R2, 1e-12), "three edges: ·√2");
        h.assertTrue(near(JointRules.jointRadius(new double[]{D, D}, true), R2, 1e-12), "forced joint: ·√2");
        h.assertTrue(near(JointRules.jointRadius(new double[]{D, 0.5}, false), 0.25, 1e-12), "the widest edge counts");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endOrTransitionFlag(GameTestHelper h) {
        h.assertTrue(JointRules.endOrTransition(new double[]{D}, false), "a lone end");
        h.assertTrue(!JointRules.endOrTransition(new double[]{D}, true), "a forced joint is not an end");
        h.assertTrue(!JointRules.endOrTransition(new double[]{D, D}, false), "two equal edges pass through");
        h.assertTrue(JointRules.endOrTransition(new double[]{D, 0.5}, false), "two different widths make a transition");
        h.assertTrue(!JointRules.endOrTransition(new double[]{D, D, D}, false), "three edges make a joint");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nodeInJointUsesSquaredRadius(GameTestHelper h) {
        h.assertTrue(JointRules.nodeInJoint(v(0, 0, 0), v(0.19, 0, 0), 0.2), "inside the joint");
        h.assertTrue(!JointRules.nodeInJoint(v(0, 0, 0), v(0.2, 0, 0), 0.2), "on the joint's edge is outside");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void angleRulesFollowCurvy(GameTestHelper h) {
        Vec3d a = v(1, 0, 0);
        h.assertTrue(!JointRules.angleTooSmall(a, atDot(0.05), R, R, R2), "wide angles pass at once");
        h.assertTrue(JointRules.angleTooSmall(a, atDot(0.9995), R, R, R2), "nearly the same direction fails at once");
        h.assertTrue(!JointRules.angleTooSmall(a, atDot(0.5), R, R, R2), "60° leaves room for both pipes");
        h.assertTrue(JointRules.angleTooSmall(a, atDot(0.9), R, R, R2), "26° does not");
        h.succeed();
    }
}
