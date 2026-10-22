package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.BorderedBandMatrix;
import com.knozyy.flowline.curve.BorderedBandSolver;
import com.knozyy.flowline.curve.ChainProblem;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.SolvedChain;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.SpanLayout;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
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
    public static void placeErrorCodesAreInOrder(GameTestHelper h) {
        PlaceError[] order = {PlaceError.INVALID_SHAPE, PlaceError.INVALID_OBJECT, PlaceError.TOO_COSTLY,
                PlaceError.TOO_FAR, PlaceError.INTERSECT_BLOCK, PlaceError.INTERSECT_PIPE, PlaceError.ANGLE_TOO_SMALL,
                PlaceError.NODE_IN_JOINT};
        for (int i = 0; i < order.length; i++) {
            h.assertTrue(order[i].code() == i + 1, order[i] + " has code " + (i + 1));
            h.assertTrue(PlaceError.byCode(i + 1).equals(Optional.of(order[i])), "code " + (i + 1) + " reads back");
        }
        h.assertTrue(PlaceError.byCode(9).isEmpty() && PlaceError.byCode(0).isEmpty(), "unknown codes are empty");
        h.assertTrue(PlaceError.TOO_FAR.key().equals("message.flowline.curve.too_far"), "lang key");
        h.succeed();
    }

    // ---- the KKT system ----------------------------------------------------------------------------------

    /** The chain problem the solver would build: chain-end conditions on the first and last span only. */
    private static ChainProblem chain(List<Vec3d> nodes, double spacing, EndCondition start, EndCondition end) {
        List<SpanLayout> spans = new ArrayList<>();
        for (int i = 0; i + 1 < nodes.size(); i++) {
            spans.add(SpanLayout.of(nodes.get(i), nodes.get(i + 1), spacing, i == 0 ? start : FREE,
                    i + 2 == nodes.size() ? end : FREE).orElseThrow());
        }
        return new ChainProblem(nodes, spacing, start, end, spans);
    }

    private static ChainProblem elbow() {
        return chain(List.of(v(0, 0, 0), v(2, 2, 0)), 0.5, dir(0, 1, 0), dir(1, 0, 0));
    }

    /** A point away from the initial guess, with non-zero multipliers, so every term of the system is exercised. */
    private static double[] disturbed(ChainProblem p) {
        double[] z = p.initialGuess();
        for (int k = 0; k < z.length; k++) {
            z[k] = p.isMultiplier(k) ? 0.1 * Math.cos(k) : z[k] + 0.01 * Math.sin(k + 1);
        }
        return z;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void straightChainHasZeroResidual(GameTestHelper h) {
        ChainProblem p = chain(List.of(v(0, 0, 0), v(4, 0, 0)), 0.5, FREE, FREE);
        double[] f = p.residual(p.initialGuess());
        for (int k = 0; k < f.length; k++) h.assertTrue(Math.abs(f[k]) < 1e-12, "residual " + k + " = " + f[k]);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void residualIsLagrangianGradient(GameTestHelper h) {
        ChainProblem p = elbow();
        double[] z = disturbed(p);
        double[] f = p.residual(z);
        double delta = 1e-6;
        for (int k = 0; k < z.length; k++) {
            double[] up = z.clone(), down = z.clone();
            up[k] += delta;
            down[k] -= delta;
            double numeric = (p.lagrangian(up) - p.lagrangian(down)) / (2 * delta);
            h.assertTrue(near(f[k], numeric, 1e-5), "∂L/∂z" + k + ": residual " + f[k] + ", numeric " + numeric);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jacobianFitsBandAndIsSymmetric(GameTestHelper h) {
        ChainProblem p = elbow();
        double[] z = disturbed(p);
        int n = p.size();
        BorderedBandMatrix m = new BorderedBandMatrix(p.bandSize(), p.spans(), ChainProblem.HALF_BAND);
        p.jacobian(z, m);
        double delta = 1e-5;
        for (int c = 0; c < n; c++) {
            double[] up = z.clone(), down = z.clone();
            up[c] += delta;
            down[c] -= delta;
            double[] fu = p.residual(up), fd = p.residual(down);
            for (int r = 0; r < n; r++) {
                double dense = (fu[r] - fd[r]) / (2 * delta);
                if (!m.holds(r, c)) {
                    h.assertTrue(Math.abs(dense) < 1e-9, "J[" + r + "][" + c + "] = " + dense + " lies outside the band");
                } else {
                    h.assertTrue(near(m.get(r, c), dense, 1e-5), "J[" + r + "][" + c + "]: " + m.get(r, c) + " vs " + dense);
                    h.assertTrue(near(m.get(r, c), m.get(c, r), 1e-5), "J is symmetric at " + r + "," + c);
                }
            }
        }
        h.succeed();
    }

    // ---- the bordered band solver ------------------------------------------------------------------------

    /** Dense Gaussian elimination with partial pivoting, to check the band solver against. */
    private static double[] denseSolve(BorderedBandMatrix m, double[] rhs) {
        int n = m.size();
        double[][] a = new double[n][n + 1];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) a[r][c] = m.get(r, c);
            a[r][n] = rhs[r];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            double[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / a[col][col];
                for (int c = col; c <= n; c++) a[r][c] -= f * a[col][c];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = a[r][n];
            for (int c = r + 1; c < n; c++) s -= a[r][c] * x[c];
            x[r] = s / a[r][r];
        }
        return x;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void borderedSolveMatchesDense(GameTestHelper h) {
        java.util.Random random = new java.util.Random(7);
        BorderedBandMatrix m = new BorderedBandMatrix(40, 2, 11);
        for (int r = 0; r < m.size(); r++) {
            for (int c = 0; c < m.size(); c++) {
                if (m.holds(r, c)) m.set(r, c, 2 * random.nextDouble() - 1);
            }
            m.set(r, r, (r % 2 == 0 ? 1 : -1) * (3 + random.nextDouble()));
        }
        double[] rhs = new double[m.size()];
        for (int r = 0; r < rhs.length; r++) rhs[r] = 2 * random.nextDouble() - 1;
        double[] x = BorderedBandSolver.solve(m, rhs);
        double[] expected = denseSolve(m, rhs);
        h.assertTrue(x != null, "a regular matrix has a solution");
        for (int r = 0; r < rhs.length; r++) h.assertTrue(near(x[r], expected[r], 1e-9), "x" + r + ": " + x[r] + " vs " + expected[r]);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void borderedSolveNeedsPivoting(GameTestHelper h) {
        BorderedBandMatrix m = new BorderedBandMatrix(6, 1, 11);
        m.set(0, 1, 1);
        m.set(1, 0, 1);
        for (int i = 2; i < 6; i++) m.set(i, i, 2);
        m.set(6, 6, 5);
        double[] rhs = {1, 2, 3, 4, 5, 6, 7};
        double[] x = BorderedBandSolver.solve(m, rhs);
        h.assertTrue(x != null, "a zero first pivot needs a row swap, not a failure");
        for (int r = 0; r < rhs.length; r++) {
            double s = 0;
            for (int c = 0; c < rhs.length; c++) s += m.get(r, c) * x[c];
            h.assertTrue(near(s, rhs[r], 1e-12), "row " + r + " of M·x");
        }
        h.succeed();
    }

    // ---- the solver ---------------------------------------------------------------------------------------

    private static Vec3d direction(List<Vec3d> points, int segment) {
        return points.get(segment + 1).sub(points.get(segment)).normalize();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void solveStraightAcceptsInitialGuess(GameTestHelper h) {
        SolvedChain chain = CurveSolver.solve(List.of(v(0, 0, 0), v(4, 0, 0)), 0.5, FREE, FREE).orElseThrow();
        h.assertTrue(chain.spans().size() == 1, "one span");
        SolvedSpan span = chain.spans().get(0);
        h.assertTrue(span.segments() == 8 && near(span.step(), 0.5, 1e-12) && span.cost() == 4,
                "8 segments of 0.5 costing 4, got " + span.segments() + " / " + span.step() + " / " + span.cost());
        for (Vec3d p : span.points()) h.assertTrue(near(p.y(), 0, 1e-12) && near(p.z(), 0, 1e-12), "straight: " + p);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void solveRejectsShortSpan(GameTestHelper h) {
        h.assertTrue(CurveSolver.solve(List.of(v(0, 0, 0), v(0.2, 0, 0)), 0.5, FREE, FREE).isEmpty(),
                "a span shorter than half the spacing has no shape");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void solveElbowBothEndsFixed(GameTestHelper h) {
        Vec3d p0 = v(0, 0, 0), p1 = v(2, 2, 0);
        EndCondition up = dir(0, 1, 0), east = dir(1, 0, 0);
        SolvedSpan span = CurveSolver.solve(List.of(p0, p1), CurveRules.DIAMETER, up, east).orElseThrow().spans().get(0);
        int expected = SpanLayout.of(p0, p1, CurveRules.DIAMETER, up, east).orElseThrow().segments();
        List<Vec3d> pts = span.points();
        h.assertTrue(span.segments() == expected, "segments " + span.segments() + " vs layout " + expected);
        h.assertTrue(direction(pts, 0).dot(up.direction()) > 1 - 1e-9, "leaves along the start direction");
        h.assertTrue(direction(pts, span.segments() - 1).dot(east.direction()) > 1 - 1e-9, "arrives along the end direction");
        double length = 0;
        for (int j = 0; j < span.segments(); j++) {
            Vec3d e = pts.get(j + 1).sub(pts.get(j));
            length += e.length();
            h.assertTrue(Math.abs(e.lengthSq() - span.step() * span.step()) < 0.1, "segment " + j + " keeps its length");
        }
        h.assertTrue(length > Math.sqrt(8), "a bent pipe is longer than its chord, got " + length);
        h.assertTrue(span.cost() == (int) Math.ceil(span.segments() * span.step()), "cost is ceil(n·h)");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void solveSmoothThroughInteriorNode(GameTestHelper h) {
        SolvedChain chain = CurveSolver.solve(List.of(v(0, 0, 0), v(2, 1, 0), v(4, 0, 0)), CurveRules.DIAMETER, FREE, FREE)
                .orElseThrow();
        h.assertTrue(chain.spans().size() == 2, "two spans");
        List<Vec3d> first = chain.spans().get(0).points(), second = chain.spans().get(1).points();
        h.assertTrue(direction(first, first.size() - 2).dot(direction(second, 0)) > 0.9, "the chain bends smoothly at the node");
        List<Vec3d> all = new ArrayList<>(first);
        all.addAll(second);
        for (Vec3d p : all) {
            double best = Double.MAX_VALUE;
            for (Vec3d q : all) best = Math.min(best, q.distanceSq(v(4 - p.x(), p.y(), p.z())));
            h.assertTrue(best < 1e-6, "the shape is mirror symmetric; no partner for " + p);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void solveRejectsNonFinite(GameTestHelper h) {
        h.assertTrue(CurveSolver.solve(List.of(v(0, 0, 0), v(Double.NaN, 0, 0)), 0.5, FREE, FREE).isEmpty(), "NaN node");
        h.assertTrue(CurveSolver.solve(List.of(v(0, 0, 0), v(2, 0, 0)), 0.5, dir(Double.POSITIVE_INFINITY, 0, 0), FREE)
                .isEmpty(), "infinite direction");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void solveLongChainIsFast(GameTestHelper h) {
        List<Vec3d> nodes = new ArrayList<>();
        for (int i = 0; i <= 8; i++) nodes.add(v(10 * i, i % 2 == 0 ? 0 : 2, 0));
        long started = System.nanoTime();
        boolean solved = CurveSolver.solve(nodes, CurveRules.DIAMETER, FREE, FREE).isPresent();
        long millis = (System.nanoTime() - started) / 1_000_000;
        h.assertTrue(solved, "an 80 block chain has a shape");
        h.assertTrue(millis < 1000, "solving 8 spans took " + millis + " ms");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void solverStopRuleHolds(GameTestHelper h) {
        for (int i = 0; i < 4; i++) h.assertTrue(CurveSolver.keepGoing(i, 1, 2), "the first four steps are always taken");
        h.assertTrue(!CurveSolver.keepGoing(4, 1, 2), "then a step that does not improve stops");
        h.assertTrue(CurveSolver.keepGoing(4, 2, 1), "an improving step goes on");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void solverRejectsFolds(GameTestHelper h) {
        h.assertTrue(!CurveSolver.acceptable(List.of(List.of(v(0, 0, 0), v(1, 0, 0), v(0.5, 0, 0))), new double[]{0.5}),
                "turning back is a fold");
        h.assertTrue(!CurveSolver.acceptable(List.of(List.of(v(0, 0, 0), v(1, 0, 0), v(1, 1, 0))), new double[]{1}),
                "a right angle between segments is refused");
        h.assertTrue(CurveSolver.acceptable(List.of(List.of(v(0, 0, 0), v(1, 0, 0), v(1.01, 1, 0))), new double[]{1}),
                "just under a right angle passes");
        h.assertTrue(!CurveSolver.acceptable(List.of(List.of(v(0, 0, 0), v(1, 0, 0))), new double[]{0}), "zero step");
        h.assertTrue(!CurveSolver.acceptable(List.of(List.of(v(0, 0, 0), v(1, 0, 0)), List.of(v(1, 0, 0), v(0, 0, 0))),
                new double[]{1, 1}), "folds across a span boundary count too");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void borderedSolveReportsSingular(GameTestHelper h) {
        h.assertTrue(BorderedBandSolver.solve(new BorderedBandMatrix(10, 1, 11), new double[11]) == null,
                "an all-zero matrix has no solution");
        h.succeed();
    }
}
