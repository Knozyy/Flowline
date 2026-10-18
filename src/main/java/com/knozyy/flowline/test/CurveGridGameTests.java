package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.curve.CurveTarget;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.GridCandidates;
import com.knozyy.flowline.curve.GridMode;
import com.knozyy.flowline.curve.Vec3d;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Curvy Pipes' grid modes and the client's pure decisions: what to target, what a click does, what the HUD says. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveGridGameTests {
    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gridCountsFollowCurvy(GameTestHelper h) {
        int[] expected = {0, 13, 24, 8, 24, 12};
        for (GridMode mode : GridMode.values()) {
            List<Vec3d> candidates = GridCandidates.of(mode, 3, 4, 5);
            h.assertTrue(candidates.size() == expected[mode.ordinal()], mode + " has " + candidates.size());
            h.assertTrue(new HashSet<>(candidates).size() == candidates.size(), mode + " repeats a candidate");
        }
        h.assertTrue(GridCandidates.of(GridMode.AXIAL, 3, 4, 5).contains(v(3.5, 4.5, 5.5)), "axial holds the centre");
        h.assertTrue(GridCandidates.of(GridMode.CORNER_N, 3, 4, 5).contains(v(3.75, 4.25, 5.75)), "inner corner");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gridModesCycleLikeCurvy(GameTestHelper h) {
        GridMode mode = GridMode.OFF;
        StringBuilder forward = new StringBuilder();
        for (int i = 0; i < 6; i++) forward.append((mode = mode.next()).ordinal());
        h.assertTrue(forward.toString().equals("123450"), "forward " + forward);
        StringBuilder back = new StringBuilder();
        for (int i = 0; i < 6; i++) back.append((mode = mode.previous()).ordinal());
        h.assertTrue(back.toString().equals("543210"), "back " + back);
        h.assertTrue(GridMode.EDGE_P.key().equals("hud.flowline.grid.edge_p"), "lang key");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gridDropsCandidatesInsideBlocks(GameTestHelper h) {
        List<Vec3d> axial = GridCandidates.of(GridMode.AXIAL, 0, 1, 0);
        List<Vec3d> free = GridCandidates.free(axial, List.of(new Aabb(0, 0, 0, 1, 1, 1)));
        h.assertTrue(!free.contains(v(0.5, 1.0, 0.5)), "the candidate on the block's top, within 0.126, is dropped");
        h.assertTrue(free.contains(v(0.5, 2.0, 0.5)) && free.contains(v(0.5, 1.5, 0.5)), "the others stay");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gridPicksNearestToTheCrosshair(GameTestHelper h) {
        Vec3d eye = v(0, 0, 0), look = v(0, 0, 1);
        Optional<Vec3d> pick = GridCandidates.pick(List.of(v(1, 0, 3), v(0.1, 0, 2), v(0, 0, -2)), eye, look);
        h.assertTrue(pick.equals(Optional.of(v(0.1, 0, 2))), "the candidate closest to the crosshair on screen, got " + pick);
        h.assertTrue(GridCandidates.pick(List.of(v(0, 0, -2)), eye, look).isEmpty(), "nothing behind the camera");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void targetPrefersNodesThenEdges(GameTestHelper h) {
        double none = Double.POSITIVE_INFINITY;
        h.assertTrue(CurveTarget.choose(2, 2.1, 5, true) == CurveTarget.Kind.NODE, "a node before its edge");
        h.assertTrue(CurveTarget.choose(2.1, 2, 5, true) == CurveTarget.Kind.NODE, "the node an edge ends at, just behind its hit");
        h.assertTrue(CurveTarget.choose(4, 2, 5, true) == CurveTarget.Kind.EDGE, "an edge well in front of a node");
        h.assertTrue(CurveTarget.choose(none, 2, 5, true) == CurveTarget.Kind.EDGE, "an edge before a block");
        h.assertTrue(CurveTarget.choose(4, 3, 1, true) == CurveTarget.Kind.BLOCK_FACE, "a block in front of both");
        h.assertTrue(CurveTarget.choose(3, none, 2, true) == CurveTarget.Kind.BLOCK_FACE, "no node through a wall");
        h.assertTrue(CurveTarget.choose(none, none, none, true) == CurveTarget.Kind.AIR, "nothing: the air");
        h.assertTrue(CurveTarget.choose(1, 1, 1, false) == CurveTarget.Kind.AIR, "without snapping only the air");
        h.succeed();
    }
}
