package com.knozyy.flowline.test;

import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.curved.EdgeInfo;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Builds curved pipes straight into the test level's {@link CurveData}, in test-relative coordinates. */
final class CurveTestSupport {
    /** Nodes each running test made, so {@link #clear} never touches a neighbouring test's curves. */
    private static final java.util.Map<GameTestHelper, List<Long>> MADE = new java.util.WeakHashMap<>();

    private CurveTestSupport() {}

    /** Remembers a node a test made through other code (e.g. a placement), for {@link #clear}. */
    static synchronized void track(GameTestHelper h, long node) {
        MADE.computeIfAbsent(h, k -> new ArrayList<>()).add(node);
    }

    static Vec3d abs(GameTestHelper h, Vec3 rel) {
        Vec3 a = h.absoluteVec(rel);
        return new Vec3d(a.x, a.y, a.z);
    }

    /** An anchor with its block given test-relative, made absolute. */
    static Anchor abs(GameTestHelper h, Anchor anchor) {
        if (anchor instanceof Anchor.PipeFace p) return new Anchor.PipeFace(h.absolutePos(p.pos()), p.face());
        if (anchor instanceof Anchor.BlockFace b) return new Anchor.BlockFace(h.absolutePos(b.pos()), b.face());
        return anchor;
    }

    static long node(GameTestHelper h, Vec3 rel, Anchor anchor, PipeType type) {
        CurveData data = CurveData.get(h.getLevel());
        long id = data.newNodeId();
        data.putNode(new CurveNode(id, abs(h, rel), false), abs(h, anchor), type);
        track(h, id);
        return id;
    }

    static long edge(GameTestHelper h, long from, long to, PipeType type, EndCondition start, EndCondition end) {
        CurveData data = CurveData.get(h.getLevel());
        Vec3d a = data.graph().node(from).orElseThrow().pos(), b = data.graph().node(to).orElseThrow().pos();
        SolvedSpan span = CurveSolver.solve(List.of(a, b), CurveRules.DIAMETER, start, end)
                .orElseThrow(() -> new GameTestAssertException("no shape from " + a + " to " + b)).spans().get(0);
        long id = data.newEdgeId();
        data.putEdge(new CurveEdge(id, from, to, CurveRules.DIAMETER, span),
                new EdgeInfo(type, PipeBlockEntity.NO_COLOR, start, end));
        return id;
    }

    /**
     * Tracks every node inside the curve structure's 9×6×9 box, for nodes made by code under test (placements).
     * Test structures never overlap, so these belong to this test.
     */
    static synchronized void trackArea(GameTestHelper h) {
        CurveData data = CurveData.get(h.getLevel());
        List<Long> made = MADE.computeIfAbsent(h, k -> new ArrayList<>());
        for (CurveNode node : data.graph().nodes()) {
            Vec3 rel = h.relativeVec(new Vec3(node.pos().x(), node.pos().y(), node.pos().z()));
            if (rel.x >= 0 && rel.x <= 9 && rel.y >= 0 && rel.y <= 6 && rel.z >= 0 && rel.z <= 9 && !made.contains(node.id())) {
                made.add(node.id());
            }
        }
    }

    /** Removes the nodes the test made and every edge that touches them. */
    static synchronized void clear(GameTestHelper h) {
        CurveData data = CurveData.get(h.getLevel());
        List<Long> nodes = MADE.getOrDefault(h, List.of());
        for (long id : nodes) {
            for (CurveEdge edge : data.graph().edgesAt(id)) data.removeEdge(edge.id());
        }
        for (long id : nodes) {
            if (data.graph().node(id).isPresent()) data.removeNode(id);
        }
        MADE.remove(h);
    }

    static BlockPos rel(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
