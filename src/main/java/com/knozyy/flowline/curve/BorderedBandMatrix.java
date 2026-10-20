package com.knozyy.flowline.curve;

/**
 * Square matrix {@code [[A, B], [D, C]]} where {@code A} is banded ({@code |row - col| <= halfBand}) and the border
 * rows and columns ({@code B}, {@code D}, {@code C}) are dense. Rows and columns use one global index: the band first,
 * then the border.
 */
public final class BorderedBandMatrix {
    private final int bandSize, border, halfBand;
    private final double[][] band;
    private final double[][] right;
    private final double[][] bottom;
    private final double[][] corner;

    public BorderedBandMatrix(int bandSize, int border, int halfBand) {
        this.bandSize = bandSize;
        this.border = border;
        this.halfBand = halfBand;
        band = new double[bandSize][2 * halfBand + 1];
        right = new double[bandSize][border];
        bottom = new double[border][bandSize];
        corner = new double[border][border];
    }

    public int size() {
        return bandSize + border;
    }

    public int bandSize() {
        return bandSize;
    }

    public int border() {
        return border;
    }

    public int halfBand() {
        return halfBand;
    }

    /** Whether ({@code row}, {@code col}) can hold a value: inside the band, or in a border row or column. */
    public boolean holds(int row, int col) {
        return row >= bandSize || col >= bandSize || Math.abs(row - col) <= halfBand;
    }

    public double get(int row, int col) {
        if (row < bandSize && col < bandSize) {
            return Math.abs(row - col) <= halfBand ? band[row][col - row + halfBand] : 0;
        }
        if (row < bandSize) return right[row][col - bandSize];
        if (col < bandSize) return bottom[row - bandSize][col];
        return corner[row - bandSize][col - bandSize];
    }

    public void set(int row, int col, double value) {
        if (!holds(row, col)) throw new IllegalArgumentException("(" + row + ", " + col + ") lies outside the band");
        if (row < bandSize && col < bandSize) band[row][col - row + halfBand] = value;
        else if (row < bandSize) right[row][col - bandSize] = value;
        else if (col < bandSize) bottom[row - bandSize][col] = value;
        else corner[row - bandSize][col - bandSize] = value;
    }

    public void addDiagonal(int i, double value) {
        set(i, i, get(i, i) + value);
    }

    public void clear() {
        for (double[] r : band) java.util.Arrays.fill(r, 0);
        for (double[] r : right) java.util.Arrays.fill(r, 0);
        for (double[] r : bottom) java.util.Arrays.fill(r, 0);
        for (double[] r : corner) java.util.Arrays.fill(r, 0);
    }
}
