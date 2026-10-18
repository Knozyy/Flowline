package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.curve.CurvePlacer;
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

    private static CurvePlacer.Action use(boolean curved, boolean drawing, CurveTarget.Kind target, boolean shift,
                                          CurvePlacer.HeldKind held) {
        return CurvePlacer.decide(curved, drawing, target, shift, CurvePlacer.Click.USE, held);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curvedModeClaimsTheUseKey(GameTestHelper h) {
        h.assertTrue(use(true, false, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.START,
                "curved mode starts a chain on the block instead of using it");
        h.assertTrue(use(true, false, CurveTarget.Kind.AIR, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.START,
                "or in the air");
        h.assertTrue(use(false, false, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.PASS,
                "Build for me leaves the click alone");
        h.assertTrue(use(true, false, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.HeldKind.OTHER) == CurvePlacer.Action.PASS,
                "a block in the main hand still places");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void drawingAddsThenFinishes(GameTestHelper h) {
        h.assertTrue(use(true, true, CurveTarget.Kind.AIR, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.ADD, "air adds a point");
        h.assertTrue(use(true, true, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.FINISH,
                "a block face finishes");
        h.assertTrue(use(true, true, CurveTarget.Kind.PIPE_FACE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.FINISH,
                "so does a pipe face");
        h.assertTrue(use(true, true, CurveTarget.Kind.NODE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.FINISH,
                "and a node");
        h.assertTrue(use(true, true, CurveTarget.Kind.AIR, true, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.FINISH,
                "shift finishes in the air");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void leftClickBreaksPointedEdges(GameTestHelper h) {
        for (boolean curved : new boolean[]{true, false}) {
            h.assertTrue(CurvePlacer.decide(curved, false, CurveTarget.Kind.EDGE, false, CurvePlacer.Click.ATTACK,
                    CurvePlacer.HeldKind.OTHER) == CurvePlacer.Action.BREAK, "an edge breaks in every mode");
            h.assertTrue(CurvePlacer.decide(curved, false, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.Click.ATTACK,
                    CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.PASS, "a block is mined as usual");
        }
        h.assertTrue(CurvePlacer.decide(true, false, CurveTarget.Kind.AIR, false, CurvePlacer.Click.PICK,
                CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.GRID_NEXT, "middle click turns the grid");
        h.assertTrue(CurvePlacer.decide(true, false, CurveTarget.Kind.AIR, true, CurvePlacer.Click.PICK,
                CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.GRID_PREVIOUS, "shift turns it back");
        h.assertTrue(CurvePlacer.decide(false, false, CurveTarget.Kind.BLOCK_FACE, false, CurvePlacer.Click.PICK,
                CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.PASS, "outside curved mode it picks the block");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endsOpenLikeSides(GameTestHelper h) {
        h.assertTrue(use(false, false, CurveTarget.Kind.NODE, false, CurvePlacer.HeldKind.EMPTY) == CurvePlacer.Action.OPEN_END,
                "an empty hand opens the end's screen");
        h.assertTrue(use(false, false, CurveTarget.Kind.NODE, true, CurvePlacer.HeldKind.WRENCH) == CurvePlacer.Action.CYCLE_END,
                "shift and the wrench cycle its mode");
        h.assertTrue(use(true, false, CurveTarget.Kind.NODE, false, CurvePlacer.HeldKind.CARD) == CurvePlacer.Action.CARD_END,
                "a Filter Card applies, even in curved mode");
        h.assertTrue(use(false, false, CurveTarget.Kind.EDGE, false, CurvePlacer.HeldKind.DYE) == CurvePlacer.Action.PAINT,
                "a dye paints the edge");
        h.succeed();
    }
}
