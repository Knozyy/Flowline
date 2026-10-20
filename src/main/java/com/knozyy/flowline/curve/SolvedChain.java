package com.knozyy.flowline.curve;

import java.util.List;

/** A solved chain: one {@link SolvedSpan} per pair of consecutive nodes. */
public record SolvedChain(List<SolvedSpan> spans) {
    public SolvedChain {
        spans = List.copyOf(spans);
    }
}
