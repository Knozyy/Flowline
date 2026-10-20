package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.RayHit;
import com.knozyy.flowline.curve.SegmentBox;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The pure curve graph: its spatial index, volume and ray queries. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveGraphGameTests {
    private static final double D = CurveRules.DIAMETER;

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    /** A straight span from {@code a} to {@code b} in segments of 0.5. */
    static SolvedSpan straight(Vec3d a, Vec3d b) {
        int segments = (int) Math.round(Math.sqrt(a.distanceSq(b)) / 0.5);
        List<Vec3d> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) points.add(a.add(b.sub(a).scale((double) i / segments)));
        return new SolvedSpan(points, 0.5);
    }

    /** A graph holding one straight edge {@code id} between new nodes {@code id·10 + 1} and {@code id·10 + 2}. */
    private static void addStraight(CurveGraph g, long id, Vec3d a, Vec3d b) {
        g.putNode(new CurveNode(id * 10 + 1, a, false));
        g.putNode(new CurveNode(id * 10 + 2, b, false));
        g.putEdge(new CurveEdge(id, id * 10 + 1, id * 10 + 2, D, straight(a, b)));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void graphIndexesAndForgetsEdges(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        addStraight(g, 1, v(0, 0.5, 0.5), v(4, 0.5, 0.5));
        List<SegmentBox> near = g.boxesNear(new Aabb(1, 0, 0, 2, 1, 1));
        h.assertTrue(!near.isEmpty() && near.stream().allMatch(b -> b.edgeId() == 1), "the edge is found where it runs");
        h.assertTrue(near.stream().map(SegmentBox::segment).distinct().count() == near.size(), "no box twice");
        h.assertTrue(g.edgesAt(11).size() == 1 && g.edgesAt(12).size() == 1, "both nodes know the edge");
        g.removeEdge(1);
        h.assertTrue(g.boxesNear(new Aabb(1, 0, 0, 2, 1, 1)).isEmpty(), "a removed edge leaves the index");
        h.assertTrue(g.edgesAt(11).isEmpty() && g.edge(1).isEmpty(), "and its nodes");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void boxesNearSkipsFarEdges(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        addStraight(g, 1, v(0, 0.5, 0.5), v(4, 0.5, 0.5));
        addStraight(g, 2, v(0, 0.5, 20.5), v(4, 0.5, 20.5));
        List<SegmentBox> near = g.boxesNear(new Aabb(0, 0, 0, 5, 1, 1));
        h.assertTrue(!near.isEmpty() && near.stream().noneMatch(b -> b.edgeId() == 2), "a far edge is not returned");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raycastPicksNearestEdge(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        addStraight(g, 1, v(0, 1, 0.5), v(4, 1, 0.5));
        addStraight(g, 2, v(0, 3, 0.5), v(4, 3, 0.5));
        RayHit hit = g.raycast(v(2.2, 5, 0.5), v(0, -1, 0), 10).orElseThrow();
        h.assertTrue(hit.edgeId() == 2, "the upper edge is nearer, got " + hit.edgeId());
        h.assertTrue(near(hit.distance(), 5 - 3 - 0.1875, 1e-9), "distance " + hit.distance());
        h.assertTrue(hit.segment() == 4 && near(hit.along(), 0.4, 1e-9), "segment 2..2.5 at 40%, got " + hit);
        h.assertTrue(near(hit.point().x(), 2.2, 1e-9) && near(hit.point().y(), 3.1875, 1e-9), "hit point " + hit.point());
        h.assertTrue(g.raycast(v(2.2, 5, 0.5), v(0, 1, 0), 10).isEmpty(), "looking away hits nothing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removingConnectedNodeFails(GameTestHelper h) {
        CurveGraph g = new CurveGraph();
        addStraight(g, 1, v(0, 0.5, 0.5), v(4, 0.5, 0.5));
        boolean refused = false;
        try {
            g.removeNode(11);
        } catch (IllegalStateException e) {
            refused = true;
        }
        h.assertTrue(refused && g.node(11).isPresent(), "a node with edges stays");
        boolean missing = false;
        try {
            g.putEdge(new CurveEdge(5, 11, 99, D, straight(v(0, 0.5, 0.5), v(1, 0.5, 0.5))));
        } catch (IllegalArgumentException e) {
            missing = true;
        }
        h.assertTrue(missing && g.edge(5).isEmpty(), "an edge to a missing node is refused");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exitDirectionPointsAwayFromNode(GameTestHelper h) {
        SolvedSpan elbow = CurveSolver.solve(List.of(v(0, 0, 0), v(2, 2, 0)), D, new EndCondition(v(0, 1, 0)),
                new EndCondition(v(1, 0, 0))).orElseThrow().spans().get(0);
        CurveEdge e = new CurveEdge(1, 11, 12, D, elbow);
        h.assertTrue(CurveGraph.exitDirection(e, 11).dot(v(0, 1, 0)) > 1 - 1e-9, "leaves the start upwards");
        h.assertTrue(CurveGraph.exitDirection(e, 12).dot(v(-1, 0, 0)) > 1 - 1e-9, "leaves the end westwards");
        h.succeed();
    }
}
