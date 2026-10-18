package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.curved.CurvedEnd;
import com.knozyy.flowline.curved.EdgeInfo;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.UpgradeType;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Curved pipe world data on its own: ids, anchors, ends and saving. Uses fresh data, not the test level's. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveDataGameTests {
    private static final Anchor.BlockFace CHEST_FACE = new Anchor.BlockFace(new BlockPos(10, 64, 10), Direction.EAST);

    private static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    private static ItemStack upgrade(UpgradeType type) {
        return new ItemStack(ModItems.upgrade(type));
    }

    /** A block-face node and a free node joined by a solved elbow, in fresh data. */
    private static long[] elbow(CurveData data) {
        long a = data.newNodeId(), b = data.newNodeId();
        Vec3d pa = v(11, 64.5, 10.5), pb = v(13, 66.5, 10.5);
        data.putNode(new CurveNode(a, pa, false), CHEST_FACE, PipeType.ITEM);
        data.putNode(new CurveNode(b, pb, false), Anchor.FREE, PipeType.ITEM);
        EndCondition start = new EndCondition(v(1, 0, 0)), end = EndCondition.FREE;
        SolvedSpan span = CurveSolver.solve(List.of(pa, pb), CurveRules.DIAMETER, start, end).orElseThrow().spans().get(0);
        long e = data.newEdgeId();
        data.putEdge(new CurveEdge(e, a, b, CurveRules.DIAMETER, span), new EdgeInfo(PipeType.ITEM, 3, start, end));
        return new long[]{a, b, e};
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void curveDataRoundTripsExactly(GameTestHelper h) {
        CurveData data = new CurveData();
        long[] ids = elbow(data);
        CurvedEnd end = data.end(ids[0]).orElseThrow();
        end.cfg().mode = SideMode.EXTRACT;
        end.cfg().setEntry(0, FilterEntry.ofItem("minecraft:dirt"));
        end.upgrades().setItem(0, upgrade(UpgradeType.SPEED));

        CurveData copy = CurveData.load(data.save(new CompoundTag()));
        CurveEdge before = data.graph().edge(ids[2]).orElseThrow(), after = copy.graph().edge(ids[2]).orElseThrow();
        h.assertTrue(after.from() == before.from() && after.to() == before.to(), "edge ends");
        h.assertTrue(Double.compare(after.shape().step(), before.shape().step()) == 0, "step");
        h.assertTrue(after.shape().points().equals(before.shape().points()), "points, bit for bit");
        h.assertTrue(copy.info(ids[2]).equals(data.info(ids[2])), "type, colour and end conditions");
        h.assertTrue(copy.graph().node(ids[1]).orElseThrow().equals(data.graph().node(ids[1]).orElseThrow()), "free node");
        h.assertTrue(copy.anchor(ids[0]).equals(CHEST_FACE) && copy.anchor(ids[1]).equals(Anchor.FREE), "anchors");
        CurvedEnd loaded = copy.end(ids[0]).orElseThrow();
        h.assertTrue(loaded.cfg().save().equals(end.cfg().save()), "the end's settings");
        h.assertTrue(ItemStack.matches(loaded.upgrades().getItem(0), upgrade(UpgradeType.SPEED)) && loaded.cfg().speedCount == 1,
                "the end's upgrade and its count");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newIdsNeverRepeat(GameTestHelper h) {
        CurveData data = new CurveData();
        long n1 = data.newNodeId(), n2 = data.newNodeId(), e1 = data.newEdgeId(), e2 = data.newEdgeId();
        h.assertTrue(n1 != n2 && e1 != e2, "fresh ids differ");
        CurveData copy = CurveData.load(data.save(new CompoundTag()));
        h.assertTrue(copy.newNodeId() > n2 && copy.newEdgeId() > e2, "and keep growing after a reload");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removingAnchoredNodeReturnsUpgrades(GameTestHelper h) {
        CurveData data = new CurveData();
        long[] ids = elbow(data);
        CurvedEnd end = data.end(ids[0]).orElseThrow();
        end.cfg().mode = SideMode.EXTRACT;
        end.upgrades().setItem(0, upgrade(UpgradeType.SPEED));
        end.upgrades().setItem(1, upgrade(UpgradeType.STACK));
        data.removeEdge(ids[2]);
        List<ItemStack> back = data.removeNode(ids[0]);
        h.assertTrue(back.size() == 2, "both upgrades come back, got " + back);
        h.assertTrue(data.graph().node(ids[0]).isEmpty() && data.end(ids[0]).isEmpty(), "the node and its end are gone");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void detachingDropsTheEndsUpgrades(GameTestHelper h) {
        CurveData data = new CurveData();
        long[] ids = elbow(data);
        CurvedEnd end = data.end(ids[0]).orElseThrow();
        end.cfg().mode = SideMode.EXTRACT;
        end.upgrades().setItem(0, upgrade(UpgradeType.SPEED));
        List<ItemStack> back = data.setAnchor(ids[0], Anchor.FREE);
        h.assertTrue(back.size() == 1 && data.end(ids[0]).isEmpty(), "leaving the face drops the end and its upgrade");
        h.assertTrue(data.setAnchor(ids[0], CHEST_FACE).isEmpty(), "attaching gives nothing back");
        CurvedEnd fresh = data.end(ids[0]).orElseThrow();
        h.assertTrue(fresh.cfg().mode == SideMode.INSERT && fresh.upgrades().isEmpty(), "and opens a fresh end");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyCurveArchiveIsUntouched(GameTestHelper h) {
        CurveData.get(h.getLevel());
        SavedData legacy = h.getLevel().getDataStorage().get(tag -> new SavedData() {
            @Override public CompoundTag save(CompoundTag t) {
                return t;
            }
        }, "flowline_curves");
        h.assertTrue(legacy == null, "the old curve archive is neither created nor read into the new data");
        h.succeed();
    }
}
