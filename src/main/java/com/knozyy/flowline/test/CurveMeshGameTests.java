package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.curve.MeshCache;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.MeshPart;
import com.knozyy.flowline.curve.MeshQuad;
import com.knozyy.flowline.curve.TubeMesh;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The glass duct mesh of curved pipes: the block pipe's profile carried along the curve. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveMeshGameTests {
    private static final double PX = 1 / 16.0;

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    private static List<Vec3d> straight(int segments) {
        List<Vec3d> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) points.add(v(i * 0.5, 0, 0));
        return points;
    }

    private static Vec3d centroid(MeshQuad q) {
        return q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25);
    }

    private static long count(List<MeshQuad> quads, MeshPart part) {
        return quads.stream().filter(q -> q.part() == part).count();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void straightTubeHasTheBlockPipeProfile(GameTestHelper h) {
        List<MeshQuad> quads = TubeMesh.edge(straight(4), TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE);
        h.assertTrue(quads.size() == 4 * (16 + 8), "4 rails and 4+4 glass faces per segment, got " + quads.size());
        for (MeshQuad q : quads) {
            for (Vec3d p : List.of(q.a(), q.b(), q.c(), q.d())) {
                double y = Math.abs(p.y()), z = Math.abs(p.z());
                if (q.part() == MeshPart.RAIL) {
                    h.assertTrue(y >= 2 * PX - 1e-9 && y <= 3 * PX + 1e-9 && z >= 2 * PX - 1e-9 && z <= 3 * PX + 1e-9,
                            "rails sit in the corners, 2–3 px from the middle: " + p);
                } else {
                    h.assertTrue(Math.abs(Math.max(y, z) - 2.5 * PX) < 1e-9, "glass is 2.5 px from the middle: " + p);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tubeQuadsFaceOutwards(GameTestHelper h) {
        List<Vec3d> points = CurveSolver.solve(List.of(v(0, 0, 0), v(2, 2, 0)), CurveRules.DIAMETER,
                new EndCondition(v(0, 1, 0)), new EndCondition(v(1, 0, 0))).orElseThrow().spans().get(0).points();
        for (MeshQuad q : TubeMesh.edge(points, TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE)) {
            if (q.part() != MeshPart.GLASS && q.part() != MeshPart.GLASS_INNER) continue;
            Vec3d c = centroid(q);
            Vec3d nearest = points.get(0);
            for (Vec3d p : points) if (p.distanceSq(c) < nearest.distanceSq(c)) nearest = p;
            double facing = q.normal().dot(c.sub(nearest));
            h.assertTrue(q.part() == MeshPart.GLASS ? facing > 0 : facing < 0,
                    q.part() + " faces the wrong way at " + c + " (" + facing + ")");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endStylesAddTheirPieces(GameTestHelper h) {
        List<MeshQuad> none = TubeMesh.edge(straight(4), TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE);
        List<MeshQuad> collar = TubeMesh.edge(straight(4), TubeMesh.EndStyle.COLLAR, TubeMesh.EndStyle.NONE);
        List<MeshQuad> ring = TubeMesh.edge(straight(4), TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.RING);
        List<MeshQuad> cap = TubeMesh.edge(straight(4), TubeMesh.EndStyle.CAP, TubeMesh.EndStyle.CAP);
        h.assertTrue(count(none, MeshPart.COLLAR) == 0 && count(none, MeshPart.RING) == 0, "no end pieces");
        h.assertTrue(count(collar, MeshPart.COLLAR) == 5, "a collar: four sides and its front, got " + count(collar, MeshPart.COLLAR));
        h.assertTrue(count(ring, MeshPart.RING) == 5, "an extract ring the same way");
        h.assertTrue(cap.size() == none.size() + 2, "a glass cap on each free end");
        h.assertTrue(TubeMesh.edge(straight(8), TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE).stream()
                .anyMatch(q -> q.part() == MeshPart.RING), "a long duct gets a band every 4 points");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jointIsAClosedSphere(GameTestHelper h) {
        Vec3d centre = v(1, 2, 3);
        double r = 0.1875 * Math.sqrt(2);
        List<MeshQuad> quads = TubeMesh.joint(centre, r, List.of(v(1, 0, 0), v(0, 1, 0), v(0, 0, -1)));
        h.assertTrue(count(quads, MeshPart.NODE_GLASS) == 8 * 6, "8 × 6 sphere quads");
        h.assertTrue(count(quads, MeshPart.RING) == 3 * 4, "a band of 4 faces at each of the 3 exits");
        for (MeshQuad q : quads) {
            if (q.part() != MeshPart.NODE_GLASS) continue;
            for (Vec3d p : List.of(q.a(), q.b(), q.c(), q.d())) {
                h.assertTrue(Math.abs(Math.sqrt(p.distanceSq(centre)) - r) < 1e-9, "sphere point off the radius: " + p);
            }
        }
        h.succeed();
    }

    private static CurveEdgesPayload.NodeSync syncNode(long id, double x) {
        return new CurveEdgesPayload.NodeSync(id, v(x, 0, 0), false, (byte) 0, false);
    }

    private static CurveEdgesPayload.EdgeSync syncEdge(long id, long from, double x0, long to, double x1) {
        return new CurveEdgesPayload.EdgeSync(id, from, to, PipeType.ITEM, -1, 0.5,
                List.of(v(x0, 0, 0), v((x0 + x1) / 2, 0, 0), v(x1, 0, 0)));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void meshCacheRebuildsOnlyChangedEdges(GameTestHelper h) {
        ClientCurves curves = new ClientCurves();
        curves.apply(new CurveEdgesPayload(new ChunkPos(0, 0), List.of(syncEdge(1, 10, 0, 11, 1), syncEdge(2, 11, 1, 12, 2)),
                List.of(syncNode(10, 0), syncNode(11, 1), syncNode(12, 2)), List.of(), List.of()));
        MeshCache cache = new MeshCache(curves);
        for (int i = 0; i < 2; i++) {
            cache.edge(1);
            cache.edge(2);
        }
        h.assertTrue(cache.builds() == 2, "each edge is built once, got " + cache.builds());
        cache.invalidate(1);
        cache.edge(1);
        cache.edge(2);
        h.assertTrue(cache.builds() == 3, "only the invalidated edge is built again, got " + cache.builds());
        curves.apply(new CurveEdgesPayload(new ChunkPos(0, 0), List.of(syncEdge(2, 11, 1, 12, 3)),
                List.of(syncNode(12, 3)), List.of(), List.of()));
        cache.edge(1);
        cache.edge(2);
        h.assertTrue(cache.builds() == 4, "a new copy from the server rebuilds that edge, got " + cache.builds());
        h.assertTrue(cache.edge(1).stream().filter(q -> q.part() == MeshPart.GLASS).count() == 2 * 4 + 1,
                "a lone free end gets a cap: 2 segments of 4 panels plus 1");
        h.succeed();
    }

    /** The vertices of {@code quads} that lie on the cross-section through {@code at} square to {@code tangent}. */
    private static List<Vec3d> ring(List<MeshQuad> quads, MeshPart part, Vec3d at, Vec3d tangent) {
        List<Vec3d> ring = new ArrayList<>();
        for (MeshQuad q : quads) {
            if (q.part() != part) continue;
            for (Vec3d p : List.of(q.a(), q.b(), q.c(), q.d())) {
                if (Math.abs(p.sub(at).dot(tangent)) < 1e-9 && p.distanceSq(at) < 0.1) ring.add(p);
            }
        }
        return ring;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chainPiecesMeetAtTheirNode(GameTestHelper h) {
        List<com.knozyy.flowline.curve.SolvedSpan> spans = CurveSolver.solve(List.of(v(0, 0, 0), v(2, 2, 0.5), v(4, 0, 1.5)),
                CurveRules.DIAMETER, new EndCondition(v(0, 1, 0)), new EndCondition(v(0, -1, 0))).orElseThrow().spans();
        List<Vec3d> a = spans.get(0).points(), b = spans.get(1).points();
        Vec3d node = a.get(a.size() - 1);
        Vec3d inA = node.sub(a.get(a.size() - 2)).normalize(), outB = b.get(1).sub(b.get(0)).normalize();
        // Curvy's solver keeps the direction through a node only to a few degrees, so the pieces share the middle one
        h.assertTrue(inA.dot(outB) < 0.9999, "this chain does bend at its node, else the test proves nothing");
        Vec3d middle = inA.add(outB).normalize();
        List<MeshQuad> qa = TubeMesh.edge(a, TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE, null, middle);
        List<MeshQuad> qb = TubeMesh.edge(b, TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE, middle, null);
        for (MeshPart part : List.of(MeshPart.RAIL, MeshPart.GLASS)) {
            List<Vec3d> endA = ring(qa, part, node, middle), startB = ring(qb, part, node, middle);
            h.assertTrue(!endA.isEmpty(), "the first piece has an end ring of " + part);
            for (Vec3d p : endA) {
                h.assertTrue(startB.stream().anyMatch(q -> q.distanceSq(p) < 1e-10),
                        part + " corner " + p + " of the first piece has no match on the second");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void meshCacheJoinsBentEdges(GameTestHelper h) {
        ClientCurves curves = new ClientCurves();
        CurveEdgesPayload.EdgeSync slight = new CurveEdgesPayload.EdgeSync(2, 11, 12, PipeType.ITEM, -1, 0.5,
                List.of(v(1, 0, 0), v(1.5, 0, 0.06), v(2, 0, 0.18)));
        CurveEdgesPayload.EdgeSync sharp = new CurveEdgesPayload.EdgeSync(3, 12, 13, PipeType.ITEM, -1, 0.5,
                List.of(v(2, 0, 0.18), v(2, 0.5, 0.18), v(2, 1, 0.18)));
        curves.apply(new CurveEdgesPayload(new ChunkPos(0, 0), List.of(syncEdge(1, 10, 0, 11, 1), slight, sharp),
                List.of(syncNode(10, 0), syncNode(11, 1), new CurveEdgesPayload.NodeSync(12, v(2, 0, 0.18), false, (byte) 0, false),
                        new CurveEdgesPayload.NodeSync(13, v(2, 1, 0.18), false, (byte) 0, false)), List.of(), List.of()));
        MeshCache cache = new MeshCache(curves);
        h.assertTrue(!cache.isJoint(11), "a slight bend is no joint");
        Vec3d node = v(1, 0, 0);
        List<MeshQuad> a = cache.edge(1), b = cache.edge(2);
        Vec3d middle = v(1, 0, 0).add(v(0.5, 0, 0.06).normalize()).normalize();
        List<Vec3d> endA = ring(a, MeshPart.RAIL, node, middle), startB = ring(b, MeshPart.RAIL, node, middle);
        h.assertTrue(!endA.isEmpty() && endA.stream().allMatch(p -> startB.stream().anyMatch(q -> q.distanceSq(p) < 1e-10)),
                "both edges end on the same section at their node");
        h.assertTrue(cache.isJoint(12) && !cache.joint(12).isEmpty(), "a right-angle bend gets a glass joint");
        h.succeed();
    }
}
