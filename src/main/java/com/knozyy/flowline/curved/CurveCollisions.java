package com.knozyy.flowline.curved;

import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveVoxels;
import com.knozyy.flowline.curve.SegmentBox;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Curved pipes as collision shapes for moving entities (EntityMixin): players and mobs stand on them and bump into
 * them, items and falling blocks land on them. The server asks its {@link CurveData}, the client its copy.
 */
public final class CurveCollisions {
    /** Slab thickness along a segment: half a pixel. */
    private static final double SLICE = 1 / 32.0;

    private CurveCollisions() {}

    @Nullable
    private static CurveGraph graph(Level level) {
        if (level instanceof ServerLevel server) return CurveData.get(server).graph();
        return level.isClientSide() ? ClientCurves.INSTANCE.graph() : null;
    }

    /**
     * Whether a curved pipe passes through the block cell at {@code pos}. Build for Me keeps its routes out of such
     * cells: a block pipe put there would break the curve.
     */
    public static boolean occupies(Level level, net.minecraft.core.BlockPos pos) {
        CurveGraph graph = graph(level);
        if (graph == null || graph.edges().isEmpty()) return false;
        Aabb cell = new Aabb(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
                .inflate(-1e-6);
        for (SegmentBox segment : graph.boxesNear(cell)) {
            if (segment.box().overlaps(cell)) return true;
        }
        return false;
    }

    /** {@code shapes} (the entity's other collisions) with the curved pipe boxes inside {@code area} added. */
    public static List<VoxelShape> with(Level level, AABB area, List<VoxelShape> shapes) {
        CurveGraph graph = graph(level);
        if (graph == null || graph.edges().isEmpty()) return shapes;
        Aabb query = new Aabb(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ);
        List<VoxelShape> out = null;
        for (SegmentBox segment : graph.boxesNear(query)) {
            if (!segment.box().bounds().intersects(query)) continue;
            for (Aabb b : CurveVoxels.boxes(segment.box(), SLICE)) {
                if (!b.intersects(query)) continue;
                if (out == null) out = new ArrayList<>(shapes);
                out.add(Shapes.create(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
        }
        return out == null ? shapes : out;
    }
}
