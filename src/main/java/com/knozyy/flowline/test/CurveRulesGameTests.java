package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.BlockShapes;
import com.knozyy.flowline.curve.CostPlan;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.FaceAttachment;
import com.knozyy.flowline.curve.JointRules;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.PlacementCandidate;
import com.knozyy.flowline.curve.PlacementValidator;
import com.knozyy.flowline.curve.Reach;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The joint, angle, reach and cost rules, and the placement validator built on them. */
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
    public static void jointRadiusFollowsTheRules(GameTestHelper h) {
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
    public static void angleRulesHold(GameTestHelper h) {
        Vec3d a = v(1, 0, 0);
        h.assertTrue(!JointRules.angleTooSmall(a, atDot(0.05), R, R, R2), "wide angles pass at once");
        h.assertTrue(JointRules.angleTooSmall(a, atDot(0.9995), R, R, R2), "nearly the same direction fails at once");
        h.assertTrue(!JointRules.angleTooSmall(a, atDot(0.5), R, R, R2), "60° leaves room for both pipes");
        h.assertTrue(JointRules.angleTooSmall(a, atDot(0.9), R, R, R2), "26° does not");
        h.succeed();
    }

    // ---- reach, cost, placement --------------------------------------------------------------------------

    private static final Aabb CUBE = new Aabb(0, 0, 0, 1, 1, 1);

    /** A world with full cubes in the given cells (as {x, y, z}) and nothing else. */
    private static BlockShapes cubes(int[]... cells) {
        return new BlockShapes() {
            private List<Aabb> at(int x, int y, int z) {
                for (int[] c : cells) {
                    if (c[0] == x && c[1] == y && c[2] == z) return List.of(new Aabb(x, y, z, x + 1, y + 1, z + 1));
                }
                return List.of();
            }

            @Override public List<Aabb> collision(int x, int y, int z) {
                return at(x, y, z);
            }

            @Override public List<Aabb> outline(int x, int y, int z) {
                return at(x, y, z);
            }
        };
    }

    private static CurveEdge edge(long id, long from, long to, Vec3d a, Vec3d b) {
        return new CurveEdge(id, from, to, D, CurveGraphGameTests.straight(a, b));
    }

    private static PlacementCandidate place(List<CurveNode> nodes, List<CurveEdge> edges) {
        return new PlacementCandidate(nodes, edges, Set.of(), Map.of());
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onFaceRulesHold(GameTestHelper h) {
        List<Aabb> cube = List.of(CUBE);
        h.assertTrue(PlacementValidator.onFace(v(0.5, 1.0099, 0.5), 1, cube), "within 0.01 of the top");
        h.assertTrue(!PlacementValidator.onFace(v(0.5, 1.0101, 0.5), 1, cube), "beyond 0.01 of the top");
        h.assertTrue(!PlacementValidator.onFace(v(1.02, 1, 0.5), 1, cube), "beside the top face");
        Vec3d[] centres = {v(0.5, 0, 0.5), v(0.5, 1, 0.5), v(0.5, 0.5, 0), v(0.5, 0.5, 1), v(0, 0.5, 0.5), v(1, 0.5, 0.5)};
        int[] opposite = {1, 0, 3, 2, 5, 4};
        for (int face = 0; face < 6; face++) {
            h.assertTrue(PlacementValidator.onFace(centres[face], face, cube), "face " + face + " holds its centre");
            h.assertTrue(!PlacementValidator.onFace(centres[opposite[face]], face, cube), "face " + face + " not the opposite");
        }
        h.assertTrue(!PlacementValidator.onFace(v(0.5, 1, 0.5), 1, List.of()), "a block without a shape has no face");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reachAddsOneBlock(GameTestHelper h) {
        h.assertTrue(!Reach.tooFar(v(0, 0, 0), 4.5, v(5.5, 0, 0)), "exactly reach + 1 is allowed");
        h.assertTrue(Reach.tooFar(v(0, 0, 0), 4.5, v(5.5001, 0, 0)), "beyond reach + 1 is too far");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void costPlanRefundsAndCharges(GameTestHelper h) {
        SolvedSpan four = CurveGraphGameTests.straight(v(0, 0, 0), v(4, 0, 0));
        SolvedSpan three = CurveGraphGameTests.straight(v(0, 0, 0), v(3, 0, 0));
        SolvedSpan five = CurveGraphGameTests.straight(v(0, 0, 0), v(5, 0, 0));
        h.assertTrue(four.cost() == 4 && three.cost() == 3 && five.cost() == 5, "span costs");
        h.assertTrue(CostPlan.required(List.of(four, three), List.of(five)) == 2, "new minus old");
        h.assertTrue(CostPlan.required(List.of(three), List.of(five)) == -2, "a shorter pipe refunds");
        h.assertTrue(!CostPlan.affordable(2, 1, false) && CostPlan.affordable(2, 1, true), "creative pays nothing");
        h.assertTrue(CostPlan.affordable(-3, 0, false), "a refund is always affordable");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateAcceptsFreeEdge(GameTestHelper h) {
        Vec3d a = v(0, 0.5, 0.5), b = v(4, 0.5, 0.5);
        PlacementCandidate c = place(List.of(new CurveNode(1, a, false), new CurveNode(2, b, false)), List.of(edge(1, 1, 2, a, b)));
        h.assertTrue(PlacementValidator.validate(new CurveGraph(), c, cubes()).isEmpty(), "nothing in the way");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateRejectsEdgeThroughBlock(GameTestHelper h) {
        Vec3d a = v(0, 0.5, 0.5), b = v(4, 0.5, 0.5);
        PlacementCandidate c = place(List.of(new CurveNode(1, a, false), new CurveNode(2, b, false)), List.of(edge(1, 1, 2, a, b)));
        h.assertTrue(PlacementValidator.validate(new CurveGraph(), c, cubes(new int[]{2, 0, 0}))
                .equals(Optional.of(PlaceError.INTERSECT_BLOCK)), "a pipe cannot go through a block");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void edgeRestingOnBlocksIsAllowed(GameTestHelper h) {
        Vec3d a = v(0, 1.1875, 0.5), b = v(4, 1.1875, 0.5);
        PlacementCandidate c = place(List.of(new CurveNode(1, a, false), new CurveNode(2, b, false)), List.of(edge(1, 1, 2, a, b)));
        BlockShapes floor = cubes(new int[]{0, 0, 0}, new int[]{1, 0, 0}, new int[]{2, 0, 0}, new int[]{3, 0, 0});
        h.assertTrue(PlacementValidator.validate(new CurveGraph(), c, floor).isEmpty(), "a pipe may lie on the floor");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateRejectsCrossingPipes(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        g.putNode(new CurveNode(11, v(0, 0.5, 2), false));
        g.putNode(new CurveNode(12, v(4, 0.5, 2), false));
        g.putEdge(edge(1, 11, 12, v(0, 0.5, 2), v(4, 0.5, 2)));
        Vec3d a = v(2, 0.5, 0), b = v(2, 0.5, 4);
        PlacementCandidate across = place(List.of(new CurveNode(21, a, false), new CurveNode(22, b, false)),
                List.of(edge(2, 21, 22, a, b)));
        h.assertTrue(PlacementValidator.validate(g, across, cubes()).equals(Optional.of(PlaceError.INTERSECT_PIPE)),
                "pipes cannot cross");
        Vec3d end = v(4, 0.5, 4);
        PlacementCandidate branch = place(List.of(new CurveNode(22, end, false)),
                List.of(edge(2, 12, 22, v(4, 0.5, 2), end)));
        h.assertTrue(PlacementValidator.validate(g, branch, cubes()).isEmpty(), "a pipe may leave another's node");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateRejectsSameDirectionBranch(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        g.putNode(new CurveNode(11, v(0, 0, 0), false));
        g.putNode(new CurveNode(12, v(4, 0, 0), false));
        g.putEdge(edge(1, 11, 12, v(0, 0, 0), v(4, 0, 0)));
        Vec3d end = v(4, 0.05, 0);
        PlacementCandidate c = place(List.of(new CurveNode(21, end, false)), List.of(edge(2, 11, 21, v(0, 0, 0), end)));
        h.assertTrue(PlacementValidator.validate(g, c, cubes()).equals(Optional.of(PlaceError.ANGLE_TOO_SMALL)),
                "two pipes cannot leave a node the same way");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateRejectsNodeInJoint(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        g.putNode(new CurveNode(1, v(0, 0, 0), false));
        g.putNode(new CurveNode(2, v(4, 0, 0), false));
        g.putNode(new CurveNode(3, v(0, 0, 4), false));
        g.putEdge(edge(1, 1, 2, v(0, 0, 0), v(4, 0, 0)));
        g.putEdge(edge(2, 1, 3, v(0, 0, 0), v(0, 0, 4)));
        Vec3d stub = v(-0.2, 0, 0);
        CurveEdge shortEdge = new CurveEdge(3, 1, 4, D, new SolvedSpan(List.of(v(0, 0, 0), stub), 0.2));
        PlacementCandidate c = place(List.of(new CurveNode(4, stub, false)), List.of(shortEdge));
        h.assertTrue(PlacementValidator.validate(g, c, cubes()).equals(Optional.of(PlaceError.NODE_IN_JOINT)),
                "a third edge makes a joint of radius 0.265, and its far end lies inside it");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validateRejectsFloatingAttachment(GameTestHelper h) {
        BlockShapes block = cubes(new int[]{0, 0, 0});
        Vec3d top = v(0.5, 1, 0.5), above = v(0.5, 1.5, 0.5);
        PlacementCandidate floating = new PlacementCandidate(
                List.of(new CurveNode(1, above, false), new CurveNode(2, v(0.5, 3.5, 0.5), false)),
                List.of(edge(1, 1, 2, above, v(0.5, 3.5, 0.5))), Set.of(), Map.of(1L, new FaceAttachment(0, 0, 0, 1)));
        h.assertTrue(PlacementValidator.validate(new CurveGraph(), floating, block)
                .equals(Optional.of(PlaceError.INTERSECT_BLOCK)), "an end half a block above the face is not on it");
        PlacementCandidate attached = new PlacementCandidate(
                List.of(new CurveNode(1, top, false), new CurveNode(2, v(0.5, 3, 0.5), false)),
                List.of(edge(1, 1, 2, top, v(0.5, 3, 0.5))), Set.of(), Map.of(1L, new FaceAttachment(0, 0, 0, 1)));
        h.assertTrue(PlacementValidator.validate(new CurveGraph(), attached, block).isEmpty(),
                "an end on the face, leaving it, is fine");
        h.succeed();
    }
}
