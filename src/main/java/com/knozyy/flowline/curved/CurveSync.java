package com.knozyy.flowline.curved;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.EdgeShape;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.network.curve.CurveForgetPayload;
import com.knozyy.flowline.pipe.SideMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps clients' copies of the curved pipes in step, chunk by chunk: a player who starts watching a chunk gets every
 * edge reaching it and their nodes; changes are collected during a tick and sent at its end to the players watching the
 * chunks they touch; a chunk a player stops watching is forgotten on their side.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class CurveSync {
    private static final class Pending {
        final Set<ChunkPos> chunks = new LinkedHashSet<>();
        final Map<ChunkPos, Set<Long>> removedEdges = new HashMap<>();
        final Map<ChunkPos, Set<Long>> removedNodes = new HashMap<>();
    }

    private static final Map<Level, Pending> PENDING = new WeakHashMap<>();

    private CurveSync() {}

    /** The chunk columns an edge's boxes reach. */
    public static Set<ChunkPos> chunksOf(CurveEdge edge) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        Aabb b = EdgeShape.bounds(EdgeShape.boxes(edge.shape(), 0.5 * edge.diameter()));
        int x0 = (int) Math.floor(b.minX()) >> 4, x1 = (int) Math.floor(b.maxX()) >> 4;
        int z0 = (int) Math.floor(b.minZ()) >> 4, z1 = (int) Math.floor(b.maxZ()) >> 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) chunks.add(new ChunkPos(x, z));
        }
        return chunks;
    }

    static ChunkPos chunkOf(Vec3d p) {
        return new ChunkPos((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4);
    }

    private static synchronized Pending pending0(Level level) {
        return PENDING.computeIfAbsent(level, l -> new Pending());
    }

    /** Chunks with changes not sent yet. */
    public static synchronized Set<ChunkPos> pending(ServerLevel level) {
        Pending pending = PENDING.get(level);
        return pending == null ? Set.of() : Set.copyOf(pending.chunks);
    }

    static synchronized void edgeChanged(ServerLevel level, CurveEdge edge) {
        pending0(level).chunks.addAll(chunksOf(edge));
    }

    static synchronized void edgeRemoved(ServerLevel level, CurveEdge edge) {
        Pending pending = pending0(level);
        for (ChunkPos chunk : chunksOf(edge)) {
            pending.chunks.add(chunk);
            pending.removedEdges.computeIfAbsent(chunk, k -> new LinkedHashSet<>()).add(edge.id());
        }
    }

    static synchronized void nodeChanged(ServerLevel level, CurveNode node) {
        pending0(level).chunks.add(chunkOf(node.pos()));
    }

    static synchronized void nodeRemoved(ServerLevel level, CurveNode node) {
        Pending pending = pending0(level);
        ChunkPos chunk = chunkOf(node.pos());
        pending.chunks.add(chunk);
        pending.removedNodes.computeIfAbsent(chunk, k -> new LinkedHashSet<>()).add(node.id());
    }

    /** Sends the tick's changes to the players watching each changed chunk. */
    public static void flush(ServerLevel level) {
        Pending pending;
        synchronized (CurveSync.class) {
            pending = PENDING.remove(level);
        }
        if (pending == null) return;
        CurveData data = CurveData.get(level);
        for (ChunkPos chunk : pending.chunks) {
            List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(chunk, false);
            if (players.isEmpty()) continue;
            CurveEdgesPayload payload = snapshot(data, chunk, pending.removedEdges.getOrDefault(chunk, Set.of()),
                    pending.removedNodes.getOrDefault(chunk, Set.of()));
            for (ServerPlayer player : players) ModNetwork.sendToPlayer(player, payload);
        }
    }

    /** The curved pipes of {@code chunk} as they are now, plus the given removals. */
    static CurveEdgesPayload snapshot(CurveData data, ChunkPos chunk, Set<Long> removedEdges, Set<Long> removedNodes) {
        List<CurveEdgesPayload.EdgeSync> edges = new ArrayList<>();
        Set<Long> nodeIds = new LinkedHashSet<>(data.nodesIn(chunk));
        for (long id : data.edgesIn(chunk)) {
            CurveEdge edge = data.graph().edge(id).orElse(null);
            if (edge == null) continue;
            EdgeInfo info = data.info(id);
            edges.add(new CurveEdgesPayload.EdgeSync(id, edge.from(), edge.to(), info.type(), info.color(),
                    edge.shape().step(), edge.shape().points()));
            nodeIds.add(edge.from());
            nodeIds.add(edge.to());
        }
        List<CurveEdgesPayload.NodeSync> nodes = new ArrayList<>();
        for (long id : nodeIds) {
            CurveNode node = data.graph().node(id).orElse(null);
            if (node == null) continue;
            Anchor anchor = data.anchor(id);
            byte kind = (byte) (anchor instanceof Anchor.PipeFace ? 1 : anchor instanceof Anchor.BlockFace ? 2 : 0);
            boolean extract = data.end(id).map(e -> e.cfg().mode == SideMode.EXTRACT).orElse(false);
            nodes.add(new CurveEdgesPayload.NodeSync(id, node.pos(), node.forceJoint(), kind, extract));
        }
        return new CurveEdgesPayload(chunk, edges, nodes, List.copyOf(removedEdges), List.copyOf(removedNodes));
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        CurveData data = CurveData.get(event.getLevel());
        if (data.edgesIn(event.getPos()).isEmpty() && data.nodesIn(event.getPos()).isEmpty()) return;
        ModNetwork.sendToPlayer(event.getPlayer(), snapshot(data, event.getPos(), Set.of(), Set.of()));
    }

    @SubscribeEvent
    public static void unwatch(ChunkWatchEvent.UnWatch event) {
        ModNetwork.sendToPlayer(event.getPlayer(), new CurveForgetPayload(event.getPos()));
    }
}
