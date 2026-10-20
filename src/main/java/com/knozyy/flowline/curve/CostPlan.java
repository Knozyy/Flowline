package com.knozyy.flowline.curve;

import java.util.Collection;

/** Pipe items an edit takes or gives back: the new spans' cost minus the replaced spans' cost. */
public final class CostPlan {
    private CostPlan() {}

    /** Items to take; negative means items to give back. */
    public static int required(Collection<SolvedSpan> added, Collection<SolvedSpan> removed) {
        int total = 0;
        for (SolvedSpan span : added) total += span.cost();
        for (SolvedSpan span : removed) total -= span.cost();
        return total;
    }

    public static boolean affordable(int required, int available, boolean creative) {
        return creative || required <= available;
    }
}
