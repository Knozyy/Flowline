package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.client.OffhandPipe;
import com.knozyy.flowline.curve.GridMode;
import com.knozyy.flowline.pipe.OffhandMode;
import net.minecraft.client.Minecraft;

/** The player's curved pipe drawing: grid mode, snapping, and the chain being laid. */
public final class CurvePlacer {
    private static GridMode grid = GridMode.AXIAL;
    private static boolean snapping = true;

    private CurvePlacer() {}

    public static GridMode grid() {
        return grid;
    }

    public static boolean snapping() {
        return snapping;
    }

    /** Whether curved placement is in hand: a pipe in the off hand in curved mode. */
    public static boolean active(Minecraft mc) {
        return OffhandPipe.holding(mc.player) && OffhandPipe.mode() == OffhandMode.CURVY;
    }
}
