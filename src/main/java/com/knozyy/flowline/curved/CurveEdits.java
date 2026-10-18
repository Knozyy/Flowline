package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CostPlan;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.CurveSolver;
import com.knozyy.flowline.curve.EdgeShape;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.FaceAttachment;
import com.knozyy.flowline.curve.Obb;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.PlacementCandidate;
import com.knozyy.flowline.curve.PlacementValidator;
import com.knozyy.flowline.curve.Reach;
import com.knozyy.flowline.curve.SolvedChain;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server side of curved pipe editing. Every operation checks in Curvy Pipes' order (curvy-behaviour-addendum-re.md,
 * B6): the objects (2), reach (4), the shape (1), the geometry (5–8), then Flowline's protection check, then the cost
 * (3). A refused edit changes nothing.
 */
public final class CurveEdits {
    private CurveEdits() {}

    /** A request end turned into a node: where it is, what it continues, how it is fixed. */
    private record Resolved(Vec3d pos, long existing, Anchor anchor, EndCondition startCondition,
                            EndCondition endCondition, @Nullable FaceAttachment attachment) {
        boolean isNew() {
            return existing < 0;
        }
    }

    /** What the edit would put into the data, kept apart until every check passed. */
    private record Plan(List<CurveNode> nodes, Map<Long, Anchor> anchors, List<CurveEdge> edges, List<EdgeInfo> infos) {}

    // ---- placing ------------------------------------------------------------------------------------------

    public static EditResult place(ServerPlayer player, PlaceRequest request) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        PipeType type = request.type();
        if (pipeType(player.getOffhandItem()) != type) return EditResult.error(PlaceError.INVALID_OBJECT);
        Resolved start = resolve(level, data, request.start(), type), end = resolve(level, data, request.end(), type);
        if (start == null || end == null || start.existing() >= 0 && start.existing() == end.existing()) {
            return EditResult.error(PlaceError.INVALID_OBJECT);
        }

        List<Vec3d> points = new ArrayList<>();
        points.add(start.pos());
        for (Vec3 p : request.internals()) points.add(new Vec3d(p.x, p.y, p.z));
        points.add(end.pos());
        for (Vec3d p : points) if (tooFar(player, p)) return EditResult.error(PlaceError.TOO_FAR);

        SolvedChain chain = CurveSolver.solve(points, CurveRules.DIAMETER, start.startCondition(), end.endCondition())
                .orElse(null);
        if (chain == null) return EditResult.error(PlaceError.INVALID_SHAPE);

        Plan plan = chainPlan(data, type, start, end, points, chain);
        Map<Long, FaceAttachment> attachments = new HashMap<>();
        long startId = plan.edges().get(0).from(), endId = plan.edges().get(plan.edges().size() - 1).to();
        if (start.attachment() != null) attachments.put(startId, start.attachment());
        if (end.attachment() != null) attachments.put(endId, end.attachment());
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(plan.nodes(), plan.edges(), Set.of(), attachments), new WorldShapes(level)).orElse(null);
        if (error != null) return EditResult.error(error);
        if (denied(player, level, plan.edges())) return EditResult.DENIED;

        int required = CostPlan.required(spans(plan.edges()), List.of());
        int available = CurveCost.available(player, type);
        if (!CostPlan.affordable(required, available, false)) return EditResult.missing(required - available);

        CurveCost.take(player, type, required);
        apply(data, type, plan);
        return EditResult.OK;
    }

    // ---- shared steps -------------------------------------------------------------------------------------

    /** The pipe type of a held pipe item, or null. */
    @Nullable
    static PipeType pipeType(ItemStack stack) {
        return PipeBlock.isPipe(stack) ? ((PipeBlock) ((BlockItem) stack.getItem()).getBlock()).type() : null;
    }

    static boolean tooFar(ServerPlayer player, Vec3d p) {
        Vec3 eye = player.getEyePosition();
        return Reach.tooFar(new Vec3d(eye.x, eye.y, eye.z), player.getBlockReach(), p);
    }

    /**
     * A request end as a node, or null when it names something that cannot be joined (Curvy's invalid_object): a
     * missing node, a node or block pipe of another type, a block pipe face that already has a curved pipe.
     */
    @Nullable
    private static Resolved resolve(ServerLevel level, CurveData data, PlaceRequest.End end, PipeType type) {
        if (end.node().isPresent()) {
            long id = end.node().getAsLong();
            CurveNode node = data.graph().node(id).orElse(null);
            if (node == null || data.nodeType(id) != type) return null;
            List<CurveEdge> edges = data.graph().edgesAt(id);
            if (edges.size() == 1 && !node.forceJoint()) {
                // an end: the new piece carries straight on from the old one
                Vec3d exit = CurveGraph.exitDirection(edges.get(0), id);
                return new Resolved(node.pos(), id, data.anchor(id), new EndCondition(exit.scale(-1)), new EndCondition(exit),
                        null);
            }
            return new Resolved(node.pos(), id, data.anchor(id), EndCondition.FREE, EndCondition.FREE, null);
        }
        if (end.face().isPresent()) {
            PlaceRequest.Face face = end.face().get();
            Direction dir = face.dir();
            Vec3d normal = new Vec3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            BlockState state = level.getBlockState(face.pos());
            if (state.getBlock() instanceof PipeBlock pipe) {
                if (pipe.type() != type || data.nodeAtPipeFace(face.pos(), dir).isPresent()) return null;
                Vec3d centre = new Vec3d(face.pos().getX() + 0.5 + 0.5 * normal.x(), face.pos().getY() + 0.5 + 0.5 * normal.y(),
                        face.pos().getZ() + 0.5 + 0.5 * normal.z());
                return new Resolved(centre, -1, new Anchor.PipeFace(face.pos().immutable(), dir), new EndCondition(normal),
                        new EndCondition(normal.scale(-1)), null);
            }
            Vec3 p = end.pos();
            return new Resolved(new Vec3d(p.x, p.y, p.z), -1, new Anchor.BlockFace(face.pos().immutable(), dir),
                    new EndCondition(normal), new EndCondition(normal.scale(-1)),
                    new FaceAttachment(face.pos().getX(), face.pos().getY(), face.pos().getZ(), dir.ordinal()));
        }
        Vec3 p = end.pos();
        return new Resolved(new Vec3d(p.x, p.y, p.z), -1, Anchor.FREE, EndCondition.FREE, EndCondition.FREE, null);
    }

    /** Nodes and edges for a solved chain: new ids for the new ends and every inner point. */
    private static Plan chainPlan(CurveData data, PipeType type, Resolved start, Resolved end, List<Vec3d> points,
                                  SolvedChain chain) {
        List<CurveNode> nodes = new ArrayList<>();
        Map<Long, Anchor> anchors = new HashMap<>();
        long[] ids = new long[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Resolved fixed = i == 0 ? start : i == points.size() - 1 ? end : null;
            if (fixed != null && !fixed.isNew()) {
                ids[i] = fixed.existing();
                continue;
            }
            ids[i] = data.newNodeId();
            nodes.add(new CurveNode(ids[i], points.get(i), false));
            anchors.put(ids[i], fixed == null ? Anchor.FREE : fixed.anchor());
        }
        List<CurveEdge> edges = new ArrayList<>();
        List<EdgeInfo> infos = new ArrayList<>();
        List<SolvedSpan> spans = chain.spans();
        for (int i = 0; i < spans.size(); i++) {
            edges.add(new CurveEdge(data.newEdgeId(), ids[i], ids[i + 1], CurveRules.DIAMETER, spans.get(i)));
            infos.add(new EdgeInfo(type, PipeBlockEntity.NO_COLOR, i == 0 ? start.startCondition() : EndCondition.FREE,
                    i == spans.size() - 1 ? end.endCondition() : EndCondition.FREE));
        }
        return new Plan(nodes, anchors, edges, infos);
    }

    private static void apply(CurveData data, PipeType type, Plan plan) {
        for (CurveNode node : plan.nodes()) data.putNode(node, plan.anchors().get(node.id()), type);
        for (int i = 0; i < plan.edges().size(); i++) data.putEdge(plan.edges().get(i), plan.infos().get(i));
    }

    static List<SolvedSpan> spans(List<CurveEdge> edges) {
        List<SolvedSpan> spans = new ArrayList<>();
        for (CurveEdge edge : edges) spans.add(edge.shape());
        return spans;
    }

    /** Every block cell the edges' boxes reach. */
    static Set<BlockPos> cells(List<CurveEdge> edges) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (CurveEdge edge : edges) {
            for (Obb box : EdgeShape.boxes(edge.shape(), 0.5 * edge.diameter())) {
                Aabb b = box.bounds();
                for (int x = (int) Math.floor(b.minX()); x <= (int) Math.floor(b.maxX()); x++) {
                    for (int y = (int) Math.floor(b.minY()); y <= (int) Math.floor(b.maxY()); y++) {
                        for (int z = (int) Math.floor(b.minZ()); z <= (int) Math.floor(b.maxZ()); z++) cells.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    /** Flowline's protection check: claim mods may refuse a placement in any cell the new pipe reaches. */
    static boolean denied(ServerPlayer player, ServerLevel level, List<CurveEdge> edges) {
        for (BlockPos cell : cells(edges)) {
            if (ForgeEventFactory.onBlockPlace(player, BlockSnapshot.create(level.dimension(), level, cell), Direction.UP)) return true;
        }
        return false;
    }
}
