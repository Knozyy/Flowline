package com.knozyy.flowline.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * The equations of one curved chain, after Curvy Pipes' solver (curvy-behaviour-addendum-re.md, B2). Every span is cut
 * into equal segments of length {@code h_i}; the chain bends as little as possible:
 * <pre>
 *   E = Σ over consecutive segments a, b (span boundaries too)  0.5·(h_a + h_b)·|e_b/h_b − e_a/h_a|²
 *   c = |e|² − h²  for every segment,   L = E + Σ λ·c
 * </pre>
 * The unknowns {@code z} are the free inner points and one multiplier per segment (the band, in chain order: a
 * segment's end point if it is free, then its multiplier) and one {@code h} per span (the border). A fixed chain end
 * makes its neighbour depend on {@code h}: {@code P0 + h·t0} at the start, {@code PN − h·tN} at the end.
 * {@link #residual} is the gradient of {@code L}, whose zero the solver looks for.
 */
public final class ChainProblem {
    /** Widest coupling between two band variables (a point and the points two segments away, in 4-wide blocks). */
    public static final int HALF_BAND = 11;
    private static final int COLOURS = 2 * HALF_BAND + 1;

    private static final int FIXED = -1, AFTER_START = -2, BEFORE_END = -3;

    private final List<Vec3d> nodes;
    private final EndCondition start, end;
    private final int spans;
    /** Per span and point j (0..m): the band index of its x, or FIXED / AFTER_START / BEFORE_END. */
    private final int[][] pointIndex;
    /** Per span and segment j: the band index of its multiplier. */
    private final int[][] multiplierIndex;
    private final boolean[] multiplier;
    private final int bandSize;
    private final List<SpanLayout> layouts;

    public ChainProblem(List<Vec3d> nodes, double spacing, EndCondition start, EndCondition end, List<SpanLayout> spans) {
        if (spans.size() != nodes.size() - 1) throw new IllegalArgumentException("one layout per span");
        this.nodes = List.copyOf(nodes);
        this.start = start;
        this.end = end;
        this.spans = spans.size();
        this.layouts = List.copyOf(spans);
        pointIndex = new int[this.spans][];
        multiplierIndex = new int[this.spans][];
        int next = 0;
        List<Integer> multipliers = new ArrayList<>();
        for (int i = 0; i < this.spans; i++) {
            int m = spans.get(i).segments();
            pointIndex[i] = new int[m + 1];
            multiplierIndex[i] = new int[m];
            pointIndex[i][0] = FIXED;
            for (int j = 0; j < m; j++) {
                int p = j + 1;
                if (p == m) pointIndex[i][p] = FIXED;
                else if (i == 0 && p == 1 && start.fixed()) pointIndex[i][p] = AFTER_START;
                else if (i == this.spans - 1 && p == m - 1 && end.fixed()) pointIndex[i][p] = BEFORE_END;
                else {
                    pointIndex[i][p] = next;
                    next += 3;
                }
                multiplierIndex[i][j] = next;
                multipliers.add(next++);
            }
        }
        bandSize = next;
        multiplier = new boolean[bandSize + this.spans];
        for (int k : multipliers) multiplier[k] = true;
    }

    public int size() {
        return bandSize + spans;
    }

    public int bandSize() {
        return bandSize;
    }

    public int spans() {
        return spans;
    }

    public boolean isMultiplier(int index) {
        return multiplier[index];
    }

    private int stepIndex(int span) {
        return bandSize + span;
    }

    public double step(double[] z, int span) {
        return z[stepIndex(span)];
    }

    public double[] initialGuess() {
        double[] z = new double[size()];
        for (int i = 0; i < spans; i++) {
            int m = layouts.get(i).segments();
            Vec3d a = nodes.get(i), b = nodes.get(i + 1);
            for (int p = 1; p < m; p++) {
                int k = pointIndex[i][p];
                if (k < 0) continue;
                Vec3d q = a.add(b.sub(a).scale((double) p / m));
                z[k] = q.x();
                z[k + 1] = q.y();
                z[k + 2] = q.z();
            }
            z[stepIndex(i)] = layouts.get(i).initialStep();
        }
        return z;
    }

    private Vec3d point(double[] z, int span, int p) {
        int k = pointIndex[span][p];
        if (k >= 0) return new Vec3d(z[k], z[k + 1], z[k + 2]);
        if (k == AFTER_START) return nodes.get(0).addScaled(start.direction(), z[stepIndex(span)]);
        if (k == BEFORE_END) return nodes.get(spans).addScaled(end.direction(), -z[stepIndex(span)]);
        return p == 0 ? nodes.get(span) : nodes.get(span + 1);
    }

    /** The solved points of every span, both nodes included. */
    public List<List<Vec3d>> points(double[] z) {
        List<List<Vec3d>> all = new ArrayList<>(spans);
        for (int i = 0; i < spans; i++) {
            List<Vec3d> span = new ArrayList<>();
            for (int p = 0; p < pointIndex[i].length; p++) span.add(point(z, i, p));
            all.add(span);
        }
        return all;
    }

    public double lagrangian(double[] z) {
        return evaluate(z, null);
    }

    public double[] residual(double[] z) {
        double[] f = new double[size()];
        evaluate(z, f);
        return f;
    }

    /** Adds {@code g}, the gradient with respect to point ({@code span}, {@code p}), to the variables it depends on. */
    private void addPointGradient(double[] f, int span, int p, Vec3d g) {
        int k = pointIndex[span][p];
        if (k >= 0) {
            f[k] += g.x();
            f[k + 1] += g.y();
            f[k + 2] += g.z();
        } else if (k == AFTER_START) {
            f[stepIndex(span)] += g.dot(start.direction());
        } else if (k == BEFORE_END) {
            f[stepIndex(span)] -= g.dot(end.direction());
        }
    }

    /** Returns L at {@code z}; when {@code f} is given, also writes the gradient of L into it. */
    private double evaluate(double[] z, double[] f) {
        double total = 0;
        int prevSpan = -1, prevSeg = -1;
        for (int i = 0; i < spans; i++) {
            double h = z[stepIndex(i)];
            for (int j = 0; j < multiplierIndex[i].length; j++) {
                Vec3d e = point(z, i, j + 1).sub(point(z, i, j));
                double lambda = z[multiplierIndex[i][j]];
                double c = e.lengthSq() - h * h;
                total += lambda * c;
                if (f != null) {
                    f[multiplierIndex[i][j]] += c;
                    Vec3d ge = e.scale(2 * lambda);
                    addPointGradient(f, i, j + 1, ge);
                    addPointGradient(f, i, j, ge.scale(-1));
                    f[stepIndex(i)] -= 2 * lambda * h;
                }
                if (prevSeg >= 0) total += bend(z, prevSpan, prevSeg, i, j, f);
                prevSpan = i;
                prevSeg = j;
            }
        }
        return total;
    }

    /** The bending term between segment ({@code sa}, {@code ja}) and the next one, ({@code sb}, {@code jb}). */
    private double bend(double[] z, int sa, int ja, int sb, int jb, double[] f) {
        double ha = z[stepIndex(sa)], hb = z[stepIndex(sb)];
        Vec3d ea = point(z, sa, ja + 1).sub(point(z, sa, ja));
        Vec3d eb = point(z, sb, jb + 1).sub(point(z, sb, jb));
        Vec3d ga = ea.scale(1 / ha), gb = eb.scale(1 / hb);
        Vec3d d = gb.sub(ga);
        double w = 0.5 * (ha + hb);
        if (f != null) {
            Vec3d dEb = d.scale(2 * w / hb);
            Vec3d dEa = d.scale(-2 * w / ha);
            addPointGradient(f, sb, jb + 1, dEb);
            addPointGradient(f, sb, jb, dEb.scale(-1));
            addPointGradient(f, sa, ja + 1, dEa);
            addPointGradient(f, sa, ja, dEa.scale(-1));
            double half = 0.5 * d.lengthSq();
            f[stepIndex(sb)] += half - 2 * w * d.dot(gb) / hb;
            f[stepIndex(sa)] += half + 2 * w * d.dot(ga) / ha;
        }
        return w * d.lengthSq();
    }

    /**
     * Writes the Jacobian of {@link #residual} (the Hessian of L, symmetric) into {@code out} by central differences.
     * Band columns {@code COLOURS} apart touch disjoint band rows, so they are perturbed together; each step column is
     * perturbed alone and the border rows are filled from it by symmetry.
     */
    public void jacobian(double[] z, BorderedBandMatrix out) {
        out.clear();
        double[] work = z.clone();
        double[] delta = new double[z.length];
        for (int k = 0; k < z.length; k++) delta[k] = 1e-6 * Math.max(1, Math.abs(z[k]));
        for (int colour = 0; colour < COLOURS && colour < bandSize; colour++) {
            for (int k = colour; k < bandSize; k += COLOURS) work[k] = z[k] + delta[k];
            double[] up = residual(work);
            for (int k = colour; k < bandSize; k += COLOURS) work[k] = z[k] - delta[k];
            double[] down = residual(work);
            for (int k = colour; k < bandSize; k += COLOURS) work[k] = z[k];
            for (int r = 0; r < bandSize; r++) {
                int k = r - HALF_BAND + Math.floorMod(colour - (r - HALF_BAND), COLOURS);
                if (k < 0 || k >= bandSize) continue;
                out.set(r, k, (up[r] - down[r]) / (2 * delta[k]));
            }
        }
        for (int i = 0; i < spans; i++) {
            int col = stepIndex(i);
            work[col] = z[col] + delta[col];
            double[] up = residual(work);
            work[col] = z[col] - delta[col];
            double[] down = residual(work);
            work[col] = z[col];
            for (int r = 0; r < size(); r++) {
                double value = (up[r] - down[r]) / (2 * delta[col]);
                out.set(r, col, value);
                if (r < bandSize) out.set(col, r, value);
            }
        }
    }
}
