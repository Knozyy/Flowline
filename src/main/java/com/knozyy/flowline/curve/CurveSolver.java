package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shapes a curved chain through its nodes, with Curvy Pipes' loop (curvy-behaviour-addendum-re.md, B2): damped Newton
 * steps on the {@link ChainProblem} until the squared residual drops below {@link CurveRules#CONVERGED}. The first
 * {@link CurveRules#FREE_ITERATIONS} steps are always taken; after that every step must improve. The tolerance is
 * loose on purpose: the result satisfies the segment lengths only to about 0.1, like Curvy's.
 */
public final class CurveSolver {
    private CurveSolver() {}

    /** The chain's shape, or empty when it has none (Curvy's {@code invalid_shape}). */
    public static Optional<SolvedChain> solve(List<Vec3d> nodes, double spacing, EndCondition start, EndCondition end) {
        if (nodes.size() < 2) return Optional.empty();
        for (Vec3d node : nodes) if (!node.isFinite()) return Optional.empty();
        if (start.fixed() && !start.direction().isFinite() || end.fixed() && !end.direction().isFinite()) {
            return Optional.empty();
        }
        List<SpanLayout> layouts = new ArrayList<>();
        for (int i = 0; i + 1 < nodes.size(); i++) {
            Optional<SpanLayout> layout = SpanLayout.of(nodes.get(i), nodes.get(i + 1), spacing,
                    i == 0 ? start : EndCondition.FREE, i + 2 == nodes.size() ? end : EndCondition.FREE);
            if (layout.isEmpty()) return Optional.empty();
            layouts.add(layout.get());
        }
        ChainProblem problem = new ChainProblem(nodes, spacing, start, end, layouts);
        double[] z = problem.initialGuess();
        double r = squared(problem.residual(z));
        if (r >= CurveRules.CONVERGED) {
            BorderedBandMatrix jacobian = new BorderedBandMatrix(problem.bandSize(), problem.spans(), ChainProblem.HALF_BAND);
            double previous = r;
            for (int iteration = 0; ; iteration++) {
                problem.jacobian(z, jacobian);
                for (int k = 0; k < problem.size(); k++) {
                    jacobian.addDiagonal(k, problem.isMultiplier(k) ? -CurveRules.REGULARISATION : CurveRules.REGULARISATION);
                }
                double[] delta = BorderedBandSolver.solve(jacobian, problem.residual(z));
                if (delta == null) return Optional.empty();
                for (int k = 0; k < z.length; k++) z[k] -= CurveRules.STEP * delta[k];
                r = squared(problem.residual(z));
                if (!Double.isFinite(r)) return Optional.empty();
                if (r < CurveRules.CONVERGED) break;
                if (iteration >= CurveRules.MAX_ITERATIONS || !keepGoing(iteration, previous, r)) return Optional.empty();
                previous = r;
            }
        }
        List<List<Vec3d>> points = problem.points(z);
        double[] steps = new double[problem.spans()];
        for (int i = 0; i < steps.length; i++) steps[i] = problem.step(z, i);
        if (!acceptable(points, steps)) return Optional.empty();
        List<SolvedSpan> spans = new ArrayList<>();
        for (int i = 0; i < steps.length; i++) spans.add(new SolvedSpan(points.get(i), steps[i]));
        return Optional.of(new SolvedChain(spans));
    }

    /** Whether the loop continues after a step that left {@code current} (it was {@code previous} before). */
    public static boolean keepGoing(int iteration, double previous, double current) {
        return iteration < CurveRules.FREE_ITERATIONS || previous > current;
    }

    /**
     * Curvy's output check: every span has a positive step and no two consecutive segments, span boundaries included,
     * turn by a right angle or more.
     */
    public static boolean acceptable(List<List<Vec3d>> spans, double[] steps) {
        Vec3d previous = null;
        for (int i = 0; i < spans.size(); i++) {
            if (!(steps[i] > 0)) return false;
            List<Vec3d> points = spans.get(i);
            for (int j = 0; j + 1 < points.size(); j++) {
                Vec3d e = points.get(j + 1).sub(points.get(j));
                if (previous != null && e.dot(previous) <= 0) return false;
                previous = e;
            }
        }
        return true;
    }

    private static double squared(double[] f) {
        double sum = 0;
        for (double x : f) sum += x * x;
        return sum;
    }
}
