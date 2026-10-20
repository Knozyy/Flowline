package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.CurveRules;
import com.knozyy.flowline.curve.GridCandidates;
import com.knozyy.flowline.curve.GridMode;
import com.knozyy.flowline.curve.RayHit;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the player points at for curved pipe work: a node, an edge, a block pipe's face, a block's face, or a point in
 * the air (a grid candidate). {@link #choose} is the pure order; {@link #find} looks into the world.
 */
public final class CurveTarget {
    public enum Kind { NODE, EDGE, PIPE_FACE, BLOCK_FACE, AIR }

    /** How close to the look ray a node must be to be picked: the radius of a joint. */
    public static final double NODE_PICK = CurveRules.RADIUS * Math.sqrt(2);

    public final Kind kind;
    public final long nodeId, edgeId;
    public final int segment;
    public final double along;
    /** The point a chain would use: the node, the hit on the edge or face, or the air point. */
    public final Vec3d pos;
    @Nullable
    public final BlockPos blockPos;
    @Nullable
    public final Direction face;

    private CurveTarget(Kind kind, long nodeId, long edgeId, int segment, double along, Vec3d pos,
                        @Nullable BlockPos blockPos, @Nullable Direction face) {
        this.kind = kind;
        this.nodeId = nodeId;
        this.edgeId = edgeId;
        this.segment = segment;
        this.along = along;
        this.pos = pos;
        this.blockPos = blockPos;
        this.face = face;
    }

    public static CurveTarget air(Vec3d pos) {
        return new CurveTarget(Kind.AIR, -1, -1, -1, 0, pos, null, null);
    }

    /**
     * The order: a node near the look ray, unless a block is in front of it; then an edge the ray hits before any block;
     * then the block (its face); else the air. Without snapping only the air counts. Distances are along the ray,
     * {@code Double.POSITIVE_INFINITY} when there is nothing.
     */
    public static Kind choose(double nodeDist, double edgeDist, double blockDist, boolean snap) {
        if (!snap) return Kind.AIR;
        if (nodeDist <= blockDist && nodeDist <= edgeDist + NODE_PICK && nodeDist < Double.POSITIVE_INFINITY) {
            return Kind.NODE;
        }
        if (edgeDist < blockDist) return Kind.EDGE;
        if (blockDist < Double.POSITIVE_INFINITY) return Kind.BLOCK_FACE;
        return Kind.AIR;
    }

    /** What the player at {@code eye} looking along unit {@code look} points at within {@code reach}. */
    public static CurveTarget find(Level level, ClientCurves curves, @Nullable Entity viewer, Vec3d eye, Vec3d look,
                                   double reach, GridMode grid, boolean snap) {
        Vec3 from = new Vec3(eye.x(), eye.y(), eye.z());
        Vec3 to = from.add(look.x() * reach, look.y() * reach, look.z() * reach);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        boolean hitBlock = block.getType() == HitResult.Type.BLOCK;
        double blockDist = hitBlock ? block.getLocation().distanceTo(from) : Double.POSITIVE_INFINITY;

        CurveNode bestNode = null;
        double nodeDist = Double.POSITIVE_INFINITY;
        for (CurveNode node : curves.graph().nodes()) {
            Vec3d rel = node.pos().sub(eye);
            double t = rel.dot(look);
            if (t <= 0 || t > reach || t >= nodeDist) continue;
            if (rel.addScaled(look, -t).lengthSq() > NODE_PICK * NODE_PICK) continue;
            bestNode = node;
            nodeDist = t;
        }
        Optional<RayHit> edge = curves.graph().raycast(eye, look, reach);
        double edgeDist = edge.map(RayHit::distance).orElse(Double.POSITIVE_INFINITY);

        Kind kind = choose(nodeDist, edgeDist, blockDist, snap);
        switch (kind) {
            case NODE:
                return new CurveTarget(Kind.NODE, bestNode.id(), -1, -1, 0, bestNode.pos(), null, null);
            case EDGE: {
                RayHit hit = edge.get();
                return new CurveTarget(Kind.EDGE, -1, hit.edgeId(), hit.segment(), hit.along(), hit.point(), null, null);
            }
            case BLOCK_FACE: {
                BlockPos pos = block.getBlockPos();
                Direction dir = block.getDirection();
                Vec3 at = block.getLocation();
                boolean pipe = level.getBlockState(pos).getBlock() instanceof PipeBlock;
                Vec3d point = pipe ? faceCentre(pos, dir) : onFace(level, pos, dir, new Vec3d(at.x, at.y, at.z), grid);
                return new CurveTarget(pipe ? Kind.PIPE_FACE : Kind.BLOCK_FACE, -1, -1, -1, 0, point, pos, dir);
            }
            default:
                break;
        }
        // In the air: the cell in front of the block the ray hits, or the cell at the end of the reach.
        double depth = hitBlock ? Math.max(0, blockDist - CurveRules.RADIUS - CurveRules.FACE_TOLERANCE) : reach;
        Vec3d end = eye.addScaled(look, depth);
        BlockPos cell = hitBlock ? block.getBlockPos().relative(block.getDirection())
                : BlockPos.containing(end.x(), end.y(), end.z());
        if (grid != GridMode.OFF) {
            List<Vec3d> free = GridCandidates.free(GridCandidates.of(grid, cell.getX(), cell.getY(), cell.getZ()),
                    outlines(level, cell));
            Optional<Vec3d> pick = GridCandidates.pick(free, eye, look);
            if (pick.isPresent()) return air(pick.get());
        }
        return air(end);
    }

    private static Vec3d faceCentre(BlockPos pos, Direction dir) {
        return new Vec3d(pos.getX() + 0.5 + dir.getStepX() * 0.5, pos.getY() + 0.5 + dir.getStepY() * 0.5,
                pos.getZ() + 0.5 + dir.getStepZ() * 0.5);
    }

    /**
     * The point on a block's face: the hit itself, or with a grid on, the hit rounded to quarter blocks across the face
     * and kept inside the face's outline.
     */
    private static Vec3d onFace(Level level, BlockPos pos, Direction dir, Vec3d hit, GridMode grid) {
        if (grid == GridMode.OFF) return hit;
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) return hit;
        AABB box = shape.bounds().move(pos);
        double x = snap(hit.x(), box.minX, box.maxX), y = snap(hit.y(), box.minY, box.maxY), z = snap(hit.z(), box.minZ, box.maxZ);
        return switch (dir.getAxis()) {
            case X -> new Vec3d(hit.x(), y, z);
            case Y -> new Vec3d(x, hit.y(), z);
            case Z -> new Vec3d(x, y, hit.z());
        };
    }

    private static double snap(double v, double min, double max) {
        double r = Math.round(v * 4) / 4.0;
        return Math.max(min, Math.min(max, r));
    }

    /** Outlines of the blocks around {@code cell}, which grid candidates must keep clear of. */
    private static List<Aabb> outlines(Level level, BlockPos cell) {
        List<Aabb> boxes = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            VoxelShape shape = level.getBlockState(p).getShape(level, p);
            for (AABB b : shape.toAabbs()) {
                boxes.add(new Aabb(b.minX + p.getX(), b.minY + p.getY(), b.minZ + p.getZ(),
                        b.maxX + p.getX(), b.maxY + p.getY(), b.maxZ + p.getZ()));
            }
        }
        return boxes;
    }
}
