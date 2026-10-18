package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.pipe.PipeType;

/** What a curved edge carries, its dye colour, and the end conditions it was solved with (to reshape it later). */
public record EdgeInfo(PipeType type, int color, EndCondition start, EndCondition end) {}
