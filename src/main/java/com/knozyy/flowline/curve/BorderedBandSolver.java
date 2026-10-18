package com.knozyy.flowline.curve;

/**
 * Solves {@code M·x = rhs} for a {@link BorderedBandMatrix} {@code [[A, B], [D, C]]}: banded LU of {@code A} with
 * partial pivoting (the upper band grows to twice its width), then the dense Schur complement
 * {@code (C − D·A⁻¹·B)} for the border.
 */
public final class BorderedBandSolver {
    private static final double TINY = 1e-300;

    private BorderedBandSolver() {}

    /** The solution, or null when the matrix is singular. Neither argument is changed. */
    public static double[] solve(BorderedBandMatrix m, double[] rhs) {
        int n = m.bandSize(), s = m.border(), kl = m.halfBand();
        int width = 3 * kl + 1;
        // a[r][c - r + kl] for columns r - kl .. r + 2·kl (room for the fill that row swaps bring)
        double[][] a = new double[n][width];
        for (int r = 0; r < n; r++) {
            for (int c = Math.max(0, r - kl); c <= Math.min(n - 1, r + kl); c++) a[r][c - r + kl] = m.get(r, c);
        }
        // right-hand sides: column 0 is rhs's band part, columns 1..s are B
        double[][] b = new double[n][s + 1];
        for (int r = 0; r < n; r++) {
            b[r][0] = rhs[r];
            for (int j = 0; j < s; j++) b[r][j + 1] = m.get(r, n + j);
        }
        for (int k = 0; k < n; k++) {
            int last = Math.min(n - 1, k + kl);
            int pivot = k;
            for (int r = k + 1; r <= last; r++) {
                if (Math.abs(a[r][k - r + kl]) > Math.abs(a[pivot][k - pivot + kl])) pivot = r;
            }
            if (Math.abs(a[pivot][k - pivot + kl]) < TINY) return null;
            int right = Math.min(n - 1, k + 2 * kl);
            if (pivot != k) {
                for (int c = k; c <= right; c++) {
                    double t = a[k][c - k + kl];
                    a[k][c - k + kl] = a[pivot][c - pivot + kl];
                    a[pivot][c - pivot + kl] = t;
                }
                double[] t = b[k];
                b[k] = b[pivot];
                b[pivot] = t;
            }
            double diagonal = a[k][kl];
            for (int r = k + 1; r <= last; r++) {
                double factor = a[r][k - r + kl] / diagonal;
                if (factor == 0) continue;
                a[r][k - r + kl] = 0;
                for (int c = k + 1; c <= right; c++) a[r][c - r + kl] -= factor * a[k][c - k + kl];
                for (int j = 0; j <= s; j++) b[r][j] -= factor * b[k][j];
            }
        }
        for (int r = n - 1; r >= 0; r--) {
            int right = Math.min(n - 1, r + 2 * kl);
            for (int j = 0; j <= s; j++) {
                double sum = b[r][j];
                for (int c = r + 1; c <= right; c++) sum -= a[r][c - r + kl] * b[c][j];
                b[r][j] = sum / a[r][kl];
            }
        }
        // b[.][0] = A⁻¹·rhs_band, b[.][1..s] = A⁻¹·B; now the border: (C − D·Y)·xh = rhs_h − D·y0
        double[][] schur = new double[s][s + 1];
        for (int i = 0; i < s; i++) {
            double right = rhs[n + i];
            for (int c = 0; c < n; c++) right -= m.get(n + i, c) * b[c][0];
            schur[i][s] = right;
            for (int j = 0; j < s; j++) {
                double value = m.get(n + i, n + j);
                for (int c = 0; c < n; c++) value -= m.get(n + i, c) * b[c][j + 1];
                schur[i][j] = value;
            }
        }
        double[] border = denseSolve(schur, s);
        if (border == null) return null;
        double[] x = new double[n + s];
        for (int r = 0; r < n; r++) {
            double value = b[r][0];
            for (int j = 0; j < s; j++) value -= b[r][j + 1] * border[j];
            x[r] = value;
        }
        System.arraycopy(border, 0, x, n, s);
        return x;
    }

    /** Gaussian elimination with partial pivoting on an augmented {@code size × (size + 1)} matrix (destroyed). */
    private static double[] denseSolve(double[][] a, int size) {
        for (int col = 0; col < size; col++) {
            int pivot = col;
            for (int r = col + 1; r < size; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            if (Math.abs(a[pivot][col]) < TINY) return null;
            double[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            for (int r = col + 1; r < size; r++) {
                double factor = a[r][col] / a[col][col];
                for (int c = col; c <= size; c++) a[r][c] -= factor * a[col][c];
            }
        }
        double[] x = new double[size];
        for (int r = size - 1; r >= 0; r--) {
            double sum = a[r][size];
            for (int c = r + 1; c < size; c++) sum -= a[r][c] * x[c];
            x[r] = sum / a[r][r];
        }
        return x;
    }
}
