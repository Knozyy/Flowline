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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
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

    /** A curved edge cut in two at a new node, which a chain end then uses (Curvy's split). */
    private record SplitPlan(long oldEdge, CurveNode middle, CurveEdge first, CurveEdge second, EdgeInfo firstInfo,
                             EdgeInfo secondInfo) {}

    private record SplitOutcome(@Nullable SplitPlan plan, @Nullable PlaceError error) {}

    // ---- placing ------------------------------------------------------------------------------------------

    public static EditResult place(ServerPlayer player, PlaceRequest request) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        PipeType type = request.type();
        if (pipeType(player.getOffhandItem()) != type) return EditResult.error(PlaceError.INVALID_OBJECT);
        List<SplitPlan> splits = new ArrayList<>();
        Resolved[] resolved = new Resolved[2];
        PlaceRequest.End[] ends = {request.start(), request.end()};
        for (int i = 0; i < 2; i++) {
            if (ends[i].split().isEmpty()) {
                resolved[i] = resolve(level, data, ends[i], type);
                continue;
            }
            PlaceRequest.Split split = ends[i].split().get();
            if (splits.stream().anyMatch(sp -> sp.oldEdge() == split.edge())) return EditResult.error(PlaceError.INVALID_OBJECT);
            SplitOutcome outcome = split(player, data, split, type);
            if (outcome.error() != null) return EditResult.error(outcome.error());
            SplitPlan plan = outcome.plan();
            splits.add(plan);
            resolved[i] = new Resolved(plan.middle().pos(), plan.middle().id(), Anchor.FREE, EndCondition.FREE,
                    EndCondition.FREE, null);
        }
        Resolved start = resolved[0], end = resolved[1];
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
        List<CurveNode> nodes = new ArrayList<>(plan.nodes());
        List<CurveEdge> edges = new ArrayList<>(plan.edges());
        Set<Long> replaced = new java.util.HashSet<>();
        List<SolvedSpan> removed = new ArrayList<>();
        for (SplitPlan split : splits) {
            nodes.add(split.middle());
            edges.add(split.first());
            edges.add(split.second());
            replaced.add(split.oldEdge());
            removed.add(data.graph().edge(split.oldEdge()).orElseThrow().shape());
        }
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(nodes, edges, replaced, attachments), new WorldShapes(level)).orElse(null);
        if (error != null) return EditResult.error(error);
        if (denied(player, level, edges)) return EditResult.DENIED;

        int required = CostPlan.required(spans(edges), removed);
        int available = CurveCost.available(player, type);
        if (!CostPlan.affordable(required, available, false)) return EditResult.missing(required - available);

        settle(player, type, required);
        for (SplitPlan split : splits) {
            data.removeEdge(split.oldEdge());
            data.putNode(split.middle(), Anchor.FREE, type);
            data.putEdge(split.first(), split.firstInfo());
            data.putEdge(split.second(), split.secondInfo());
        }
        apply(data, type, plan);
        return EditResult.OK;
    }

    // ---- breaking and removing ----------------------------------------------------------------------------

    /** Breaks one curved edge: its pipes come back, nodes left without edges go (their upgrades drop). */
    public static EditResult breakEdge(ServerPlayer player, long edgeId) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurveEdge edge = data.graph().edge(edgeId).orElse(null);
        if (edge == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (edge.shape().points().stream().allMatch(p -> tooFar(player, p))) return EditResult.error(PlaceError.TOO_FAR);
        if (breakDenied(player, level, List.of(edge))) return EditResult.DENIED;
        PipeType type = data.info(edgeId).type();
        data.removeEdge(edgeId);
        CurveEffects.broken(level, edge, type);
        CurveCost.give(player, type, edge.shape().cost());
        dropLonely(level, data, edge.from());
        dropLonely(level, data, edge.to());
        return EditResult.OK;
    }

    /**
     * Removes a node: an end takes its edge with it, a node between two edges is bridged by one new edge (the cost
     * difference is settled), a joint of three or more edges stays (invalid_object).
     */
    public static EditResult removeNode(ServerPlayer player, long nodeId) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurveNode node = data.graph().node(nodeId).orElse(null);
        if (node == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (tooFar(player, node.pos())) return EditResult.error(PlaceError.TOO_FAR);
        List<CurveEdge> edges = data.graph().edgesAt(nodeId);
        if (edges.isEmpty()) {
            drop(level, node.pos(), data.removeNode(nodeId));
            return EditResult.OK;
        }
        if (edges.size() == 1) return breakEdge(player, edges.get(0).id());
        if (edges.size() > 2) return EditResult.error(PlaceError.INVALID_OBJECT);

        CurveEdge a = edges.get(0), b = edges.get(1);
        long farA = a.from() == nodeId ? a.to() : a.from(), farB = b.from() == nodeId ? b.to() : b.from();
        EndCondition start = leaving(data, a, farA), end = arriving(data, b, farB);
        Vec3d pa = data.graph().node(farA).orElseThrow().pos(), pb = data.graph().node(farB).orElseThrow().pos();
        SolvedChain chain = CurveSolver.solve(List.of(pa, pb), CurveRules.DIAMETER, start, end).orElse(null);
        if (chain == null) return EditResult.error(PlaceError.INVALID_SHAPE);
        EdgeInfo infoA = data.info(a.id());
        CurveEdge merged = new CurveEdge(data.newEdgeId(), farA, farB, CurveRules.DIAMETER, chain.spans().get(0));
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(List.of(), List.of(merged), Set.of(a.id(), b.id()), Map.of()), new WorldShapes(level))
                .orElse(null);
        if (error != null) return EditResult.error(error);
        if (denied(player, level, List.of(merged)) || breakDenied(player, level, List.of(a, b))) return EditResult.DENIED;
        int required = CostPlan.required(List.of(merged.shape()), List.of(a.shape(), b.shape()));
        int available = CurveCost.available(player, infoA.type());
        if (!CostPlan.affordable(required, available, false)) return EditResult.missing(required - available);

        settle(player, infoA.type(), required);
        data.removeEdge(a.id());
        data.removeEdge(b.id());
        drop(level, node.pos(), data.removeNode(nodeId));
        data.putEdge(merged, new EdgeInfo(infoA.type(), infoA.color(), start, end));
        return EditResult.OK;
    }

    // ---- inserting, moving, forcing ------------------------------------------------------------------------

    /** The two halves of an edge split for a chain end, or why it cannot be split. Nothing is written. */
    private static SplitOutcome split(ServerPlayer player, CurveData data, PlaceRequest.Split split, PipeType type) {
        CurveEdge edge = data.graph().edge(split.edge()).orElse(null);
        if (edge == null || data.info(edge.id()).type() != type || split.segment() < 0
                || split.segment() >= edge.shape().segments() || !(split.along() >= 0 && split.along() <= 1)) {
            return new SplitOutcome(null, PlaceError.INVALID_OBJECT);
        }
        List<Vec3d> p = edge.shape().points();
        Vec3d point = p.get(split.segment()).add(p.get(split.segment() + 1).sub(p.get(split.segment())).scale(split.along()));
        if (tooFar(player, point)) return new SplitOutcome(null, PlaceError.TOO_FAR);
        EdgeInfo info = data.info(edge.id());
        Vec3d a = data.graph().node(edge.from()).orElseThrow().pos(), b = data.graph().node(edge.to()).orElseThrow().pos();
        SolvedChain chain = CurveSolver.solve(List.of(a, point, b), CurveRules.DIAMETER, info.start(), info.end()).orElse(null);
        if (chain == null) return new SplitOutcome(null, PlaceError.INVALID_SHAPE);
        CurveNode middle = new CurveNode(data.newNodeId(), point, false);
        CurveEdge first = new CurveEdge(data.newEdgeId(), edge.from(), middle.id(), edge.diameter(), chain.spans().get(0));
        CurveEdge second = new CurveEdge(data.newEdgeId(), middle.id(), edge.to(), edge.diameter(), chain.spans().get(1));
        return new SplitOutcome(new SplitPlan(edge.id(), middle, first, second,
                new EdgeInfo(info.type(), info.color(), info.start(), EndCondition.FREE),
                new EdgeInfo(info.type(), info.color(), EndCondition.FREE, info.end())), null);
    }

    /** Splits an edge with a new node {@code along} (0–1) its segment {@code segment}; both halves are re-solved. */
    public static EditResult insert(ServerPlayer player, long edgeId, int segment, double along) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurveEdge edge = data.graph().edge(edgeId).orElse(null);
        if (edge == null || segment < 0 || segment >= edge.shape().segments() || !(along >= 0 && along <= 1)) {
            return EditResult.error(PlaceError.INVALID_OBJECT);
        }
        List<Vec3d> p = edge.shape().points();
        Vec3d point = p.get(segment).add(p.get(segment + 1).sub(p.get(segment)).scale(along));
        if (tooFar(player, point)) return EditResult.error(PlaceError.TOO_FAR);
        EdgeInfo info = data.info(edgeId);
        Vec3d a = data.graph().node(edge.from()).orElseThrow().pos(), b = data.graph().node(edge.to()).orElseThrow().pos();
        SolvedChain chain = CurveSolver.solve(List.of(a, point, b), CurveRules.DIAMETER, info.start(), info.end()).orElse(null);
        if (chain == null) return EditResult.error(PlaceError.INVALID_SHAPE);
        CurveNode middle = new CurveNode(data.newNodeId(), point, false);
        CurveEdge first = new CurveEdge(data.newEdgeId(), edge.from(), middle.id(), edge.diameter(), chain.spans().get(0));
        CurveEdge second = new CurveEdge(data.newEdgeId(), middle.id(), edge.to(), edge.diameter(), chain.spans().get(1));
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(List.of(middle), List.of(first, second), Set.of(edgeId), Map.of()), new WorldShapes(level))
                .orElse(null);
        if (error != null) return EditResult.error(error);
        if (denied(player, level, List.of(first, second))) return EditResult.DENIED;
        int required = CostPlan.required(List.of(first.shape(), second.shape()), List.of(edge.shape()));
        int available = CurveCost.available(player, info.type());
        if (!CostPlan.affordable(required, available, false)) return EditResult.missing(required - available);

        settle(player, info.type(), required);
        data.removeEdge(edgeId);
        data.putNode(middle, Anchor.FREE, info.type());
        data.putEdge(first, new EdgeInfo(info.type(), info.color(), info.start(), EndCondition.FREE));
        data.putEdge(second, new EdgeInfo(info.type(), info.color(), EndCondition.FREE, info.end()));
        return EditResult.OK;
    }

    /**
     * Moves a node to {@code pos}, fixed to {@code face} if given. Every edge touching it is re-solved on its own;
     * leaving a block face drops the end's settings and upgrades (curvy-behaviour-addendum-re.md, B5).
     */
    public static EditResult translate(ServerPlayer player, long nodeId, Vec3 pos, java.util.Optional<PlaceRequest.Face> face) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurveNode node = data.graph().node(nodeId).orElse(null);
        if (node == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        PipeType type = data.nodeType(nodeId);
        Resolved target = resolve(level, data, new PlaceRequest.End(pos, java.util.OptionalLong.empty(), face), type);
        if (target == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (tooFar(player, target.pos())) return EditResult.error(PlaceError.TOO_FAR);

        CurveNode moved = new CurveNode(nodeId, target.pos(), node.forceJoint());
        List<CurveEdge> reshaped = new ArrayList<>();
        List<EdgeInfo> infos = new ArrayList<>();
        Set<Long> replaced = new java.util.HashSet<>();
        for (CurveEdge edge : data.graph().edgesAt(nodeId)) {
            EdgeInfo info = data.info(edge.id());
            boolean fromMoved = edge.from() == nodeId;
            EndCondition start = fromMoved ? target.startCondition() : info.start();
            EndCondition end = fromMoved ? info.end() : target.endCondition();
            Vec3d a = fromMoved ? target.pos() : data.graph().node(edge.from()).orElseThrow().pos();
            Vec3d b = fromMoved ? data.graph().node(edge.to()).orElseThrow().pos() : target.pos();
            SolvedChain chain = CurveSolver.solve(List.of(a, b), CurveRules.DIAMETER, start, end).orElse(null);
            if (chain == null) return EditResult.error(PlaceError.INVALID_SHAPE);
            reshaped.add(new CurveEdge(edge.id(), edge.from(), edge.to(), edge.diameter(), chain.spans().get(0)));
            infos.add(new EdgeInfo(info.type(), info.color(), start, end));
            replaced.add(edge.id());
        }
        Map<Long, FaceAttachment> attachments = target.attachment() == null ? Map.of() : Map.of(nodeId, target.attachment());
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(List.of(moved), reshaped, replaced, attachments), new WorldShapes(level)).orElse(null);
        if (error != null) return EditResult.error(error);
        if (denied(player, level, reshaped)) return EditResult.DENIED;
        List<SolvedSpan> old = new ArrayList<>();
        for (long id : replaced) old.add(data.graph().edge(id).orElseThrow().shape());
        int required = CostPlan.required(spans(reshaped), old);
        int available = CurveCost.available(player, type);
        if (!CostPlan.affordable(required, available, false)) return EditResult.missing(required - available);

        settle(player, type, required);
        if (!data.anchor(nodeId).equals(target.anchor())) drop(level, node.pos(), data.setAnchor(nodeId, target.anchor()));
        data.putNode(moved, target.anchor(), type);
        for (int i = 0; i < reshaped.size(); i++) data.putEdge(reshaped.get(i), infos.get(i));
        return EditResult.OK;
    }

    /** Turns a node's forced joint on or off, if the bigger (or smaller) joint still fits (Curvy's 7 and 8). */
    public static EditResult forceJoint(ServerPlayer player, long nodeId, boolean desired) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurveNode node = data.graph().node(nodeId).orElse(null);
        if (node == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (tooFar(player, node.pos())) return EditResult.error(PlaceError.TOO_FAR);
        CurveNode changed = new CurveNode(nodeId, node.pos(), desired);
        PlaceError error = PlacementValidator.validate(data.graph(),
                new PlacementCandidate(List.of(changed), List.of(), Set.of(), Map.of()), new WorldShapes(level)).orElse(null);
        if (error != null) return EditResult.error(error);
        data.putNode(changed, data.anchor(nodeId), data.nodeType(nodeId));
        return EditResult.OK;
    }

    // ---- curved ends --------------------------------------------------------------------------------------

    /** The end on node {@code nodeId} the player may use: it exists, is in reach, and its block holds what it carries. */
    @Nullable
    // ---- painting -----------------------------------------------------------------------------------------

    /**
     * Dyes a curved edge with the dye in {@code hand}, like a block pipe: its own colour again washes it off, a new
     * colour costs one dye in survival. Block pipe faces at its ends are worked out again, so a colour that no longer
     * matches lets go.
     */
    public static EditResult paint(ServerPlayer player, long edgeId, InteractionHand hand) {
        CurveData data = CurveData.get(player.serverLevel());
        CurveEdge edge = data.graph().edge(edgeId).orElse(null);
        ItemStack stack = player.getItemInHand(hand);
        if (edge == null || !(stack.getItem() instanceof DyeItem dye)) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (edge.shape().points().stream().allMatch(p -> tooFar(player, p))) return EditResult.error(PlaceError.TOO_FAR);
        EdgeInfo info = data.info(edgeId);
        int colour = dye.getDyeColor().getId();
        boolean wash = info.color() == colour;
        data.putEdge(edge, new EdgeInfo(info.type(), wash ? PipeBlockEntity.NO_COLOR : colour, info.start(), info.end()));
        if (!wash && !player.getAbilities().instabuild) stack.shrink(1);
        return EditResult.OK;
    }

    private static CurvedEnd usableEnd(ServerPlayer player, CurveData data, long nodeId) {
        CurvedEnd end = data.end(nodeId).orElse(null);
        CurveNode node = data.graph().node(nodeId).orElse(null);
        if (end == null || node == null) return null;
        return tooFar(player, node.pos()) ? null : end;
    }

    /** Opens the end's screen, the same one a block pipe side has. */
    public static EditResult openEnd(ServerPlayer player, long nodeId) {
        ServerLevel level = player.serverLevel();
        CurveData data = CurveData.get(level);
        CurvedEnd end = usableEnd(player, data, nodeId);
        if (end == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        if (!end.type().hasEndpoint(level, end.block(), end.access())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flowline.no_endpoint"), true);
            return EditResult.error(PlaceError.INVALID_OBJECT);
        }
        net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable("gui.flowline.pipe_config",
                CurveCost.item(end.type()).getDescription(),
                net.minecraft.network.chat.Component.translatable("direction.flowline." + end.access().getName()));
        com.knozyy.flowline.menu.PipeMenu.open(player, new CurvedEndHost(level, end), title);
        return EditResult.OK;
    }

    /**
     * The wrench on a curved end: insert ↔ extract. A block pipe side's third step, disconnected, has no curved
     * counterpart: breaking the edge is how a curved end lets go.
     */
    public static EditResult cycleEnd(ServerPlayer player, long nodeId) {
        CurveData data = CurveData.get(player.serverLevel());
        CurvedEnd end = usableEnd(player, data, nodeId);
        if (end == null) return EditResult.error(PlaceError.INVALID_OBJECT);
        boolean extract = end.cfg().mode != com.knozyy.flowline.pipe.SideMode.EXTRACT;
        end.cfg().mode = extract ? com.knozyy.flowline.pipe.SideMode.EXTRACT : com.knozyy.flowline.pipe.SideMode.INSERT;
        if (!extract) {
            for (ItemStack upgrade : end.takeExtractOnlyUpgrades()) player.getInventory().placeItemBackInInventory(upgrade);
        }
        end.cfg().resetRuntime();
        data.endChanged(nodeId);
        return EditResult.OK;
    }

    /** The Filter Card on a curved end, as on a block pipe side: sneak to copy, otherwise paste. */
    public static EditResult useCardOnEnd(ServerPlayer player, long nodeId, net.minecraft.world.InteractionHand hand) {
        CurveData data = CurveData.get(player.serverLevel());
        CurvedEnd end = usableEnd(player, data, nodeId);
        ItemStack stack = player.getItemInHand(hand);
        if (end == null || !(stack.getItem() instanceof com.knozyy.flowline.item.FilterCardItem)) {
            return EditResult.error(PlaceError.INVALID_OBJECT);
        }
        com.knozyy.flowline.item.FilterCardItem.useOnConfig(end.cfg(), end.type(),
                net.minecraft.network.chat.Component.translatable("direction.flowline." + end.access().getName()), player, stack,
                () -> data.endChanged(nodeId));
        return EditResult.OK;
    }

    /** The condition of a chain that leaves node {@code far} along {@code edge}'s own condition there. */
    static EndCondition leaving(CurveData data, CurveEdge edge, long far) {
        EdgeInfo info = data.info(edge.id());
        if (edge.from() == far) return info.start();
        return info.end().fixed() ? new EndCondition(info.end().direction().scale(-1)) : EndCondition.FREE;
    }

    /** The condition of a chain that arrives at node {@code far} along {@code edge}'s own condition there. */
    static EndCondition arriving(CurveData data, CurveEdge edge, long far) {
        EdgeInfo info = data.info(edge.id());
        if (edge.to() == far) return info.end();
        return info.start().fixed() ? new EndCondition(info.start().direction().scale(-1)) : EndCondition.FREE;
    }

    /** Takes {@code required} pipes, or gives {@code -required} back. */
    static void settle(ServerPlayer player, PipeType type, int required) {
        if (required > 0) CurveCost.take(player, type, required);
        else CurveCost.give(player, type, -required);
    }

    /** Removes a node that has no edges left, dropping its end's upgrades where it stood. */
    static void dropLonely(ServerLevel level, CurveData data, long nodeId) {
        CurveNode node = data.graph().node(nodeId).orElse(null);
        if (node != null && data.graph().edgesAt(nodeId).isEmpty()) drop(level, node.pos(), data.removeNode(nodeId));
    }

    static void drop(ServerLevel level, Vec3d at, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, at.x(), at.y(),
                    at.z(), stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    /** Flowline's protection check for taking pipes away: a break event in every cell the edges reach. */
    static boolean breakDenied(ServerPlayer player, ServerLevel level, List<CurveEdge> edges) {
        for (BlockPos cell : cells(edges)) {
            var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, cell, level.getBlockState(cell), player);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return true;
        }
        return false;
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
