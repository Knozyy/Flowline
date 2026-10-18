package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeNetwork;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The curved pipes of one level: the geometry ({@link CurveGraph}), where each node is fixed, the ends that touch
 * blocks, and what each edge carries. Saved as {@code flowline_curved_pipes}; the pre-0.3 {@code flowline_curves}
 * archive is a different file and stays untouched.
 */
public final class CurveData extends SavedData {
    public static final String NAME = "flowline_curved_pipes";
    private static final int VERSION = 1;

    private final CurveGraph graph = new CurveGraph();
    private final Map<Long, Anchor> anchors = new HashMap<>();
    private final Map<Long, PipeType> nodeTypes = new HashMap<>();
    private final Map<Long, CurvedEnd> ends = new LinkedHashMap<>();
    private final Map<Long, EdgeInfo> infos = new HashMap<>();
    /** {@code pos.asLong()·8 + face} of a block pipe face → the node on it. */
    private final Map<Long, Long> pipeFaces = new HashMap<>();
    /** Block → nodes fixed to one of its faces. */
    private final Map<BlockPos, Set<Long>> anchoredTo = new HashMap<>();
    private final Map<net.minecraft.world.level.ChunkPos, Set<Long>> edgesByChunk = new HashMap<>();
    private final Map<Long, Set<net.minecraft.world.level.ChunkPos>> chunksByEdge = new HashMap<>();
    private final Map<net.minecraft.world.level.ChunkPos, Set<Long>> nodesByChunk = new HashMap<>();
    private long nextNode = 1, nextEdge = 1;
    /** The level this data belongs to; null for data made outside a level (tests, loading). */
    private ServerLevel level;

    public static CurveData get(ServerLevel level) {
        CurveData data = level.getDataStorage().computeIfAbsent(CurveData::load, CurveData::new, NAME);
        data.level = level;
        return data;
    }

    // ---- reading ------------------------------------------------------------------------------------------

    public CurveGraph graph() {
        return graph;
    }

    public Anchor anchor(long node) {
        return anchors.getOrDefault(node, Anchor.FREE);
    }

    public PipeType nodeType(long node) {
        return nodeTypes.get(node);
    }

    public Optional<CurvedEnd> end(long node) {
        return Optional.ofNullable(ends.get(node));
    }

    public Collection<CurvedEnd> ends() {
        return Collections.unmodifiableCollection(ends.values());
    }

    public EdgeInfo info(long edge) {
        return infos.get(edge);
    }

    public OptionalLong nodeAtPipeFace(BlockPos pos, Direction face) {
        Long node = pipeFaces.get(faceKey(pos, face));
        return node == null ? OptionalLong.empty() : OptionalLong.of(node);
    }

    public List<Long> nodesAnchoredTo(BlockPos pos) {
        return List.copyOf(anchoredTo.getOrDefault(pos, Set.of()));
    }

    /** Edges whose boxes reach chunk column {@code chunk}. */
    public Set<Long> edgesIn(net.minecraft.world.level.ChunkPos chunk) {
        return Set.copyOf(edgesByChunk.getOrDefault(chunk, Set.of()));
    }

    /** Nodes standing in chunk column {@code chunk}. */
    public Set<Long> nodesIn(net.minecraft.world.level.ChunkPos chunk) {
        return Set.copyOf(nodesByChunk.getOrDefault(chunk, Set.of()));
    }

    // ---- writing ------------------------------------------------------------------------------------------

    public long newNodeId() {
        setDirty();
        return nextNode++;
    }

    public long newEdgeId() {
        setDirty();
        return nextEdge++;
    }

    /** Adds the node (or replaces the one with the same id, keeping its end) fixed by {@code anchor}. */
    public void putNode(CurveNode node, Anchor anchor, PipeType type) {
        graph.node(node.id()).ifPresent(old -> unindexNode(old));
        graph.putNode(node);
        nodesByChunk.computeIfAbsent(CurveSync.chunkOf(node.pos()), k -> new LinkedHashSet<>()).add(node.id());
        if (level != null) CurveSync.nodeChanged(level, node);
        nodeTypes.put(node.id(), type);
        if (!anchor(node.id()).equals(anchor) || !anchors.containsKey(node.id())) setAnchor(node.id(), anchor);
        setDirty();
        nodeChanged(node.id());
    }

    public void putEdge(CurveEdge edge, EdgeInfo info) {
        graph.edge(edge.id()).ifPresent(this::unindexEdge);
        graph.putEdge(edge);
        infos.put(edge.id(), info);
        Set<net.minecraft.world.level.ChunkPos> chunks = CurveSync.chunksOf(edge);
        chunksByEdge.put(edge.id(), chunks);
        for (var chunk : chunks) edgesByChunk.computeIfAbsent(chunk, k -> new LinkedHashSet<>()).add(edge.id());
        if (level != null) CurveSync.edgeChanged(level, edge);
        setDirty();
        nodeChanged(edge.from());
        nodeChanged(edge.to());
    }

    public void removeEdge(long id) {
        Optional<CurveEdge> edge = graph.edge(id);
        edge.ifPresent(e -> {
            unindexEdge(e);
            if (level != null) CurveSync.edgeRemoved(level, e);
        });
        graph.removeEdge(id);
        infos.remove(id);
        setDirty();
        edge.ifPresent(e -> {
            nodeChanged(e.from());
            nodeChanged(e.to());
        });
    }

    /** Removes a node without edges; gives back the upgrades of its end, if it had one. */
    public List<ItemStack> removeNode(long id) {
        Optional<CurveNode> gone = graph.node(id);
        graph.removeNode(id);
        gone.ifPresent(n -> {
            unindexNode(n);
            if (level != null) CurveSync.nodeRemoved(level, n);
        });
        Anchor old = anchor(id);
        List<ItemStack> upgrades = unanchor(id);
        anchors.remove(id);
        nodeTypes.remove(id);
        setDirty();
        if (level != null) PipeNetwork.invalidateNode(level, id);
        anchorChanged(old);
        return upgrades;
    }

    /**
     * Fixes the node somewhere else. Leaving a block face drops the end there (its upgrades are returned); arriving
     * on one opens a fresh end.
     */
    public List<ItemStack> setAnchor(long node, Anchor anchor) {
        Anchor old = anchor(node);
        List<ItemStack> upgrades = unanchor(node);
        anchors.put(node, anchor);
        if (anchor instanceof Anchor.PipeFace face) pipeFaces.put(faceKey(face.pos(), face.face()), node);
        if (anchor instanceof Anchor.BlockFace face) {
            ends.put(node, new CurvedEnd(node, nodeTypes.getOrDefault(node, PipeType.ITEM), face, this::setDirty));
        }
        BlockPos block = blockOf(anchor);
        if (block != null) anchoredTo.computeIfAbsent(block, k -> new LinkedHashSet<>()).add(node);
        setDirty();
        anchorChanged(old);
        nodeChanged(node);
        return upgrades;
    }

    private void unindexEdge(CurveEdge edge) {
        Set<net.minecraft.world.level.ChunkPos> chunks = chunksByEdge.remove(edge.id());
        if (chunks == null) return;
        for (var chunk : chunks) {
            Set<Long> set = edgesByChunk.get(chunk);
            if (set != null && set.remove(edge.id()) && set.isEmpty()) edgesByChunk.remove(chunk);
        }
    }

    private void unindexNode(CurveNode node) {
        var chunk = CurveSync.chunkOf(node.pos());
        Set<Long> set = nodesByChunk.get(chunk);
        if (set != null && set.remove(node.id()) && set.isEmpty()) nodesByChunk.remove(chunk);
    }

    /** Clients see the change of an end's mode (its green ring) at the next tick's end. */
    public void endChanged(long node) {
        setDirty();
        if (level == null) return;
        graph.node(node).ifPresent(n -> CurveSync.nodeChanged(level, n));
        for (CurveEdge edge : graph.edgesAt(node)) CurveSync.edgeChanged(level, edge);
        PipeNetwork.invalidateNode(level, node);
    }

    /** Networks through the node are rebuilt, and a block pipe the node sits on redraws that face. */
    private void nodeChanged(long node) {
        if (level == null) return;
        PipeNetwork.invalidateNode(level, node);
        anchorChanged(anchor(node));
    }

    private void anchorChanged(Anchor anchor) {
        if (level == null || !(anchor instanceof Anchor.PipeFace face)) return;
        PipeNetwork.invalidate(level, face.pos());
        PipeBlock.updateConnections(level, face.pos());
    }

    private List<ItemStack> unanchor(long node) {
        Anchor old = anchors.get(node);
        if (old == null) return List.of();
        if (old instanceof Anchor.PipeFace face) pipeFaces.remove(faceKey(face.pos(), face.face()), node);
        BlockPos block = blockOf(old);
        if (block != null) {
            Set<Long> set = anchoredTo.get(block);
            if (set != null && set.remove(node) && set.isEmpty()) anchoredTo.remove(block);
        }
        CurvedEnd end = ends.remove(node);
        anchors.put(node, Anchor.FREE);
        return end == null ? List.of() : end.takeUpgrades();
    }

    private static BlockPos blockOf(Anchor anchor) {
        if (anchor instanceof Anchor.PipeFace face) return face.pos();
        if (anchor instanceof Anchor.BlockFace face) return face.pos();
        return null;
    }

    private static long faceKey(BlockPos pos, Direction face) {
        return pos.asLong() * 8 + face.ordinal();
    }

    // ---- world changes ------------------------------------------------------------------------------------

    /** Blocks that changed this tick; looked at once at the end of the tick. Not saved. */
    private final Set<BlockPos> dirty = new LinkedHashSet<>();

    public void markDirty(BlockPos pos) {
        dirty.add(pos.immutable());
    }

    /**
     * Curvy Pipes' answer to changed blocks (curvy-behaviour-addendum-re.md, B4): an edge whose boxes now overlap a
     * changed block's collision shape breaks, its pipes drop where it met the block, and nodes left without edges go.
     * Nodes fixed to a changed block keep their anchor and settings; their networks are rebuilt.
     */
    public void processDirty(ServerLevel level) {
        if (dirty.isEmpty()) return;
        List<BlockPos> changed = new ArrayList<>(dirty);
        dirty.clear();
        for (BlockPos pos : changed) {
            for (long node : nodesAnchoredTo(pos)) PipeNetwork.invalidateNode(level, node);
            if (!level.isLoaded(pos)) continue;
            List<com.knozyy.flowline.curve.Aabb> solids =
                    WorldShapes.boxes(level.getBlockState(pos).getCollisionShape(level, pos), pos);
            if (solids.isEmpty()) continue;
            com.knozyy.flowline.curve.Aabb cell = new com.knozyy.flowline.curve.Aabb(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            Map<Long, Vec3d> broken = new LinkedHashMap<>();
            for (com.knozyy.flowline.curve.SegmentBox box : graph.boxesNear(cell)) {
                if (broken.containsKey(box.edgeId())) continue;
                for (com.knozyy.flowline.curve.Aabb solid : solids) {
                    if (box.box().overlaps(solid)) {
                        broken.put(box.edgeId(), box.box().center());
                        break;
                    }
                }
            }
            for (Map.Entry<Long, Vec3d> entry : broken.entrySet()) {
                CurveEdge edge = graph.edge(entry.getKey()).orElse(null);
                if (edge == null) continue;
                EdgeInfo info = infos.get(edge.id());
                removeEdge(edge.id());
                int cost = edge.shape().cost();
                net.minecraft.world.item.Item item = CurveCost.item(info.type());
                while (cost > 0) {
                    int part = Math.min(cost, item.getMaxStackSize());
                    CurveEdits.drop(level, entry.getValue(), List.of(new ItemStack(item, part)));
                    cost -= part;
                }
                CurveEdits.dropLonely(level, this, edge.from());
                CurveEdits.dropLonely(level, this, edge.to());
            }
        }
    }

    // ---- saving -------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        tag.putLong("nextNode", nextNode);
        tag.putLong("nextEdge", nextEdge);
        ListTag nodes = new ListTag();
        for (CurveNode node : graph.nodes()) {
            CompoundTag n = new CompoundTag();
            n.putLong("id", node.id());
            n.put("pos", vec(node.pos()));
            n.putBoolean("force", node.forceJoint());
            n.putString("type", nodeTypes.get(node.id()).name());
            Anchor anchor = anchor(node.id());
            CompoundTag a = new CompoundTag();
            if (anchor instanceof Anchor.PipeFace face) {
                a.putString("kind", "pipe");
                a.putLong("pos", face.pos().asLong());
                a.putInt("face", face.face().ordinal());
            } else if (anchor instanceof Anchor.BlockFace face) {
                a.putString("kind", "block");
                a.putLong("pos", face.pos().asLong());
                a.putInt("face", face.face().ordinal());
            } else {
                a.putString("kind", "free");
            }
            n.put("anchor", a);
            CurvedEnd end = ends.get(node.id());
            if (end != null) n.put("end", end.save());
            nodes.add(n);
        }
        tag.put("nodes", nodes);
        ListTag edges = new ListTag();
        for (CurveEdge edge : graph.edges()) {
            EdgeInfo info = infos.get(edge.id());
            CompoundTag e = new CompoundTag();
            e.putLong("id", edge.id());
            e.putLong("from", edge.from());
            e.putLong("to", edge.to());
            e.putDouble("diameter", edge.diameter());
            e.putString("type", info.type().name());
            e.putInt("color", info.color());
            e.putDouble("step", edge.shape().step());
            List<Vec3d> points = edge.shape().points();
            long[] bits = new long[points.size() * 3];
            for (int i = 0; i < points.size(); i++) {
                bits[3 * i] = Double.doubleToRawLongBits(points.get(i).x());
                bits[3 * i + 1] = Double.doubleToRawLongBits(points.get(i).y());
                bits[3 * i + 2] = Double.doubleToRawLongBits(points.get(i).z());
            }
            e.putLongArray("points", bits);
            if (info.start().fixed()) e.put("start", vec(info.start().direction()));
            if (info.end().fixed()) e.put("end", vec(info.end().direction()));
            edges.add(e);
        }
        tag.put("edges", edges);
        return tag;
    }

    public static CurveData load(CompoundTag tag) {
        CurveData data = new CurveData();
        data.nextNode = Math.max(1, tag.getLong("nextNode"));
        data.nextEdge = Math.max(1, tag.getLong("nextEdge"));
        List<CompoundTag> endTags = new ArrayList<>();
        List<Long> endNodes = new ArrayList<>();
        for (Tag t : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag) t;
            long id = n.getLong("id");
            CompoundTag a = n.getCompound("anchor");
            Anchor anchor = switch (a.getString("kind")) {
                case "pipe" -> new Anchor.PipeFace(BlockPos.of(a.getLong("pos")), Direction.values()[a.getInt("face")]);
                case "block" -> new Anchor.BlockFace(BlockPos.of(a.getLong("pos")), Direction.values()[a.getInt("face")]);
                default -> Anchor.FREE;
            };
            data.putNode(new CurveNode(id, vec(n.getList("pos", Tag.TAG_DOUBLE)), n.getBoolean("force")), anchor,
                    PipeType.valueOf(n.getString("type")));
            if (n.contains("end")) {
                endTags.add(n.getCompound("end"));
                endNodes.add(id);
            }
        }
        for (int i = 0; i < endTags.size(); i++) data.ends.get(endNodes.get(i)).load(endTags.get(i));
        for (Tag t : tag.getList("edges", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            long[] bits = e.getLongArray("points");
            List<Vec3d> points = new ArrayList<>(bits.length / 3);
            for (int i = 0; i + 2 < bits.length; i += 3) {
                points.add(new Vec3d(Double.longBitsToDouble(bits[i]), Double.longBitsToDouble(bits[i + 1]),
                        Double.longBitsToDouble(bits[i + 2])));
            }
            EndCondition start = e.contains("start") ? new EndCondition(vec(e.getList("start", Tag.TAG_DOUBLE))) : EndCondition.FREE;
            EndCondition end = e.contains("end") ? new EndCondition(vec(e.getList("end", Tag.TAG_DOUBLE))) : EndCondition.FREE;
            data.putEdge(new CurveEdge(e.getLong("id"), e.getLong("from"), e.getLong("to"), e.getDouble("diameter"),
                    new SolvedSpan(points, e.getDouble("step"))),
                    new EdgeInfo(PipeType.valueOf(e.getString("type")), e.getInt("color"), start, end));
        }
        data.setDirty(false);
        return data;
    }

    private static ListTag vec(Vec3d v) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(v.x()));
        list.add(DoubleTag.valueOf(v.y()));
        list.add(DoubleTag.valueOf(v.z()));
        return list;
    }

    private static Vec3d vec(ListTag list) {
        return new Vec3d(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }
}
