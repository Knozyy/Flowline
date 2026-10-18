package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.BlockShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** The level's block shapes for the curve validator, in world coordinates; unloaded cells are empty. */
public final class WorldShapes implements BlockShapes {
    private final ServerLevel level;

    public WorldShapes(ServerLevel level) {
        this.level = level;
    }

    @Override
    public List<Aabb> collision(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) return List.of();
        return boxes(level.getBlockState(pos).getCollisionShape(level, pos), pos);
    }

    @Override
    public List<Aabb> outline(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) return List.of();
        return boxes(level.getBlockState(pos).getShape(level, pos), pos);
    }

    static List<Aabb> boxes(VoxelShape shape, BlockPos pos) {
        List<Aabb> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            boxes.add(new Aabb(box.minX + pos.getX(), box.minY + pos.getY(), box.minZ + pos.getZ(),
                    box.maxX + pos.getX(), box.maxY + pos.getY(), box.maxZ + pos.getZ()));
        }
        return boxes;
    }
}
