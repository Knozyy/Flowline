package com.knozyy.flowline.curve;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.pipe.*;
import com.knozyy.flowline.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Dimension-owned graph. Geometry never implies connectivity; edges explicitly name their endpoints. */
public final class CurveData extends SavedData {
    public static final int MAX_NODES = 4096, MAX_EDGES = 8192;
    public record Edge(int id, int a, int b, int paid, int units) {
        public Edge(int id,int a,int b,int paid){this(id,a,b,paid,paid);}
    }
    private final Map<Integer, CurveNode> nodes = new LinkedHashMap<>();
    private final Map<Integer, Edge> edges = new LinkedHashMap<>();
    private final Map<Integer, Set<Integer>> links = new HashMap<>();
    private final Map<Integer, CurveGeometry.Shape> shapes = new HashMap<>();
    private final Map<Long,Set<Integer>> chunks = new HashMap<>();
    private boolean indexDirty = true;
    private final ServerLevel level;
    private int next = 1;
    private long revision;
    public CurveData(ServerLevel level) { this.level = level; }
    public static CurveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), () -> new CurveData(level), "flowline_curves");
    }
    public Map<Integer, CurveNode> nodes() { return Collections.unmodifiableMap(nodes); }
    public Map<Integer, Edge> edges() { return Collections.unmodifiableMap(edges); }
    public long revision() { return revision; }
    public ServerLevel level() { return level; }
    public void updatePaid(List<Edge> affected, boolean creative) {
        for (Edge e : affected) {
            int units=Math.max(e.units,(int)Math.ceil(shape(e).length())),extra=Math.max(0,units-e.units);
            edges.put(e.id,new Edge(e.id,e.a,e.b,e.paid+(creative?0:extra),units));
        }
        changed();
    }
    public void setBudget(Edge e,int paid,int units){edges.put(e.id,new Edge(e.id,e.a,e.b,paid,units));changed();}
    public void changed() { revision++; setDirty(); }
    public Set<Integer> links(int node) { return Collections.unmodifiableSet(links.getOrDefault(node, Set.of())); }
    public static PipeBlock block(PipeType type) {
        return switch(type) {
            case ITEM -> ModBlocks.ITEM_PIPE.get(); case FLUID -> ModBlocks.FLUID_PIPE.get();
            case ENERGY -> ModBlocks.ENERGY_PIPE.get(); case UNIVERSAL -> ModBlocks.UNIVERSAL_PIPE.get();
            case CHEMICAL -> ModBlocks.CHEMICAL_PIPE == null ? ModBlocks.ITEM_PIPE.get() : ModBlocks.CHEMICAL_PIPE.get();
        };
    }
    public boolean mayEdit(Player player, CurveNode n) {
        return player.mayBuild() && (n.owner.equals(player.getUUID()) || player.hasPermissions(2))
                && player.level() == level && player.distanceToSqr(n.point) <= CurveGeometry.REACH*CurveGeometry.REACH
                && level.isLoaded(BlockPos.containing(n.point)) && level.mayInteract(player, BlockPos.containing(n.point));
    }
    public CurveNode addNode(PipeType type, UUID owner, Vec3 point, BlockPos block, Direction face) {
        if (nodes.size() >= MAX_NODES || next == Integer.MAX_VALUE || !CurveGeometry.finite(point)) return null;
        CurveNode node = new CurveNode(next++, type, owner, point, block, face);
        if (block != null) node.config = new CurveNode.Endpoint(this, node, level);
        nodes.put(node.id, node); links.put(node.id, new LinkedHashSet<>()); changed();
        return node;
    }
    public Edge connect(int a, int b, int paid) {
        if (a == b || !nodes.containsKey(a) || !nodes.containsKey(b) || edges.size() >= MAX_EDGES
                || nodes.get(a).type != nodes.get(b).type || next == Integer.MAX_VALUE) return null;
        for (int id : links(a)) { Edge e = edges.get(id); if (other(e, a) == b) return null; }
        Edge edge = new Edge(next++, a, b, paid);
        edges.put(edge.id, edge); links.get(a).add(edge.id); links.get(b).add(edge.id);
        invalidate(); return edge;
    }
    public static int other(Edge e, int id) { return e.a == id ? e.b : e.a; }
    public void invalidate() { shapes.clear(); indexDirty=true; for (CurveNode n : nodes.values()) if (n.config != null) n.config.wake(); changed(); }
    public List<net.minecraft.world.phys.shapes.VoxelShape> collisions(net.minecraft.world.phys.AABB box) {
        if(indexDirty){
            chunks.clear();
            for(Edge e:edges.values()){
                var b=shape(e).bounds();
                for(int x=((int)Math.floor(b.minX))>>4;x<=((int)Math.floor(b.maxX))>>4;x++)
                    for(int z=((int)Math.floor(b.minZ))>>4;z<=((int)Math.floor(b.maxZ))>>4;z++)
                        chunks.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(x,z),k->new HashSet<>()).add(e.id);
            }indexDirty=false;
        }
        Set<Integer> candidates=new HashSet<>();
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)
            for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)
                candidates.addAll(chunks.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),Set.of()));
        List<net.minecraft.world.phys.shapes.VoxelShape> result=new ArrayList<>();
        for(int id:candidates)result.addAll(CurveCollision.boxes(shape(edges.get(id)),box));return result;
    }
    public Edge removeEdge(int id) {
        Edge e = edges.remove(id);
        if (e != null) { links.get(e.a).remove(id); links.get(e.b).remove(id); invalidate(); }
        return e;
    }
    /** Roll back a split without changing the original edge identity or its material budget. */
    public void restoreEdge(Edge edge) {
        edges.put(edge.id,edge);links.get(edge.a).add(edge.id);links.get(edge.b).add(edge.id);invalidate();
    }
    public void removeNode(int id) {
        for (int edge : List.copyOf(links(id))) removeEdge(edge);
        CurveNode n = nodes.remove(id); links.remove(id);
        if (n != null && n.config != null) n.config.setRemoved();
        invalidate();
    }
    public CurveGeometry.Shape shape(Edge e) {
        return shapes.computeIfAbsent(e.id, id -> {
            CurveNode a = nodes.get(e.a), b = nodes.get(e.b);
            double scale = a.point.distanceTo(b.point) * 0.75;
            return new CurveGeometry.Shape(a.point, b.point, tangent(a, b, true).scale(scale), tangent(b, a, false).scale(scale));
        });
    }
    private Vec3 tangent(CurveNode n, CurveNode other, boolean start) {
        Vec3 d;
        if (n.block != null) d = Vec3.atLowerCornerOf(n.face.getNormal());
        else if (!n.joint && links(n.id).size() == 2) {
            int previous = links(n.id).stream().map(edges::get).mapToInt(e -> other(e, n.id)).filter(id -> id != other.id).findFirst().orElse(other.id);
            d = other.point.subtract(nodes.get(previous).point);
        } else d = other.point.subtract(n.point);
        return d.normalize().scale(start ? 1 : -1);
    }
    public Set<Integer> component(int start) {
        Set<Integer> found = new LinkedHashSet<>(); ArrayDeque<Integer> q = new ArrayDeque<>();
        if (!nodes.containsKey(start)) return found;
        found.add(start); q.add(start);
        while (!q.isEmpty() && found.size() <= FlowlineConfig.MAX_NETWORK_SIZE.get()) {
            int id = q.remove();
            for (int eid : links(id)) { int n = other(edges.get(eid), id); if (found.add(n)) q.add(n); }
        }
        return found;
    }
    public List<PipeNetwork.Target> targets(CurveNode source) {
        Map<Integer, Integer> distance = new HashMap<>(); ArrayDeque<Integer> queue = new ArrayDeque<>();
        distance.put(source.id, 0); queue.add(source.id);
        List<PipeNetwork.Target> targets = new ArrayList<>();
        while (!queue.isEmpty() && distance.size() <= FlowlineConfig.MAX_NETWORK_SIZE.get()) {
            int id = queue.remove(); CurveNode n = nodes.get(id);
            if (id != source.id && n.active && n.config != null && n.config.side(n.side()).mode == SideMode.INSERT
                    && level.isLoaded(n.block) && n.type.hasEndpoint(level, n.block, n.face)
                    && !n.block.equals(source.block)) {
                targets.add(new PipeNetwork.Target(n.block.relative(n.face), n.side(), distance.get(id),
                        Caps.create(n.type, level, n.block, n.face), n.config, List.of()));
            }
            for (int eid : links(id)) {
                int next = other(edges.get(eid), id);
                if (!distance.containsKey(next)) { distance.put(next, distance.get(id)+1); queue.add(next); }
            }
        }
        SideConfig cfg = source.config.side(source.side());
        Comparator<PipeNetwork.Target> nearest = Comparator.comparingInt(PipeNetwork.Target::distance);
        targets.sort(cfg.distribution == Distribution.FARTHEST ? nearest.reversed()
                : cfg.distribution == Distribution.PRIORITY ? Comparator.<PipeNetwork.Target>comparingInt(t -> t.insert().priority).reversed().thenComparing(nearest) : nearest);
        if (cfg.distribution == Distribution.RANDOM) Collections.shuffle(targets);
        if ((cfg.distribution == Distribution.ROUND_ROBIN || cfg.distribution == Distribution.BALANCED) && !targets.isEmpty())
            Collections.rotate(targets, -Math.floorMod(cfg.roundRobin++, targets.size()));
        return targets;
    }
    public void tick() {
        for (CurveNode n : nodes.values()) {
            if (!n.active || n.config == null || !level.isLoaded(n.block)) continue;
            SideConfig cfg = n.config.side(n.side());
            if (cfg.mode != SideMode.EXTRACT) continue;
            boolean signal = level.hasNeighborSignal(n.block);
            if (cfg.redstone == RedstoneMode.PULSE) {
                if (!signal) { cfg.pulsePending = true; continue; }
                if (!cfg.pulsePending) continue;
                cfg.pulsePending = false;
            } else if (!cfg.redstone.allows(signal)) continue;
            if (cfg.interval < 0) cfg.interval = Pacing.start(cfg.speedCount);
            if (--cfg.cooldown > 0) continue;
            int elapsed = cfg.interval;
            List<PipeNetwork.Target> targets = targets(n);
            long moved = n.type.transfer(level, n.block, Caps.create(n.type, level, n.block, n.face), cfg, targets, elapsed,
                    (t, item) -> CurveServer.animate(this, n, t, item, null),
                    (t, fluid) -> CurveServer.animate(this, n, t, net.minecraft.world.item.ItemStack.EMPTY, fluid));
            cfg.interval = moved > 0 ? Pacing.afterWork(cfg.interval, cfg.speedCount) : Pacing.afterIdle(cfg.interval, cfg.speedCount);
            cfg.cooldown = cfg.interval;
        }
    }
    public List<Integer> route(int from, int to) {
        Map<Integer,Integer> parent = new HashMap<>(), via = new HashMap<>(); ArrayDeque<Integer> q = new ArrayDeque<>();
        parent.put(from, from); q.add(from);
        while (!q.isEmpty() && !parent.containsKey(to) && parent.size() <= FlowlineConfig.MAX_NETWORK_SIZE.get()) {
            int n = q.remove();
            for (int eid : links(n)) { int next = other(edges.get(eid), n); if (!parent.containsKey(next)) { parent.put(next,n); via.put(next,eid); q.add(next); } }
        }
        if (!parent.containsKey(to)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (int n=to; n!=from; n=parent.get(n)) result.add(via.get(n));
        Collections.reverse(result); return result;
    }
    public static void writePoint(CompoundTag t, Vec3 p) { t.putDouble("x",p.x); t.putDouble("y",p.y); t.putDouble("z",p.z); }
    public static Vec3 readPoint(CompoundTag t) { return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")); }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("next",next); ListTag ns = new ListTag(), es = new ListTag();
        for (CurveNode n : nodes.values()) {
            CompoundTag t = new CompoundTag(); t.putInt("id",n.id); t.putString("type",n.type.name()); t.putUUID("owner",n.owner);
            writePoint(t,n.point); t.putBoolean("joint",n.joint); t.putBoolean("active",n.active);
            if (n.block != null) { t.putLong("block",n.block.asLong()); t.putInt("face",n.face.ordinal()); t.put("config",n.config.saveWithoutMetadata()); }
            ns.add(t);
        }
        for (Edge e : edges.values()) { CompoundTag t = new CompoundTag(); t.putInt("id",e.id); t.putInt("a",e.a); t.putInt("b",e.b); t.putInt("paid",e.paid);t.putInt("units",e.units); es.add(t); }
        tag.put("nodes",ns); tag.put("edges",es); return tag;
    }
    public static CurveData load(ServerLevel level, CompoundTag tag) {
        CurveData d = new CurveData(level);
        ListTag ns = tag.getList("nodes",Tag.TAG_COMPOUND), es = tag.getList("edges",Tag.TAG_COMPOUND);
        for (int i=0; i<Math.min(MAX_NODES,ns.size()); i++) {
            CompoundTag t = ns.getCompound(i); int id=t.getInt("id"); Vec3 p=readPoint(t);
            if (id<=0 || id==Integer.MAX_VALUE || !CurveGeometry.finite(p) || !t.hasUUID("owner") || d.nodes.containsKey(id)) continue;
            PipeType type; try { type=PipeType.valueOf(t.getString("type")); } catch(IllegalArgumentException ex) { continue; }
            if (type == PipeType.CHEMICAL && ModBlocks.CHEMICAL_PIPE == null) continue;
            BlockPos block=t.contains("block")?BlockPos.of(t.getLong("block")):null;
            CurveNode n=new CurveNode(id,type,t.getUUID("owner"),p,block,Direction.from3DDataValue(t.getInt("face")));
            n.joint=t.getBoolean("joint"); n.active=t.getBoolean("active");
            if(block!=null) { n.config=new CurveNode.Endpoint(d,n,level); n.config.load(t.getCompound("config")); }
            d.nodes.put(id,n); d.links.put(id,new LinkedHashSet<>()); d.next=Math.max(d.next,id+1);
        }
        for(int i=0;i<Math.min(MAX_EDGES,es.size());i++) {
            CompoundTag t=es.getCompound(i);int paid=Math.max(0,Math.min(64,t.getInt("paid")));
            Edge e=new Edge(t.getInt("id"),t.getInt("a"),t.getInt("b"),paid,t.contains("units")?Math.max(paid,Math.min(64,t.getInt("units"))):paid);
            if(e.id<=0 || e.id==Integer.MAX_VALUE || e.a==e.b || d.edges.containsKey(e.id) || !d.nodes.containsKey(e.a)||!d.nodes.containsKey(e.b)||d.nodes.get(e.a).type!=d.nodes.get(e.b).type
                    ||d.nodes.get(e.a).point.distanceTo(d.nodes.get(e.b).point)>CurveGeometry.MAX_LENGTH) continue;
            d.edges.put(e.id,e);d.links.get(e.a).add(e.id);d.links.get(e.b).add(e.id);d.next=Math.max(d.next,e.id+1);
        }
        return d;
    }
}
