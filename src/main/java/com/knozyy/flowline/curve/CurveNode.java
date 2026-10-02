package com.knozyy.flowline.curve;

import com.knozyy.flowline.pipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class CurveNode {
    public final int id;
    public final PipeType type;
    public final UUID owner;
    public Vec3 point;
    public BlockPos block;
    public Direction face;
    public boolean joint, active = true;
    public Endpoint config;

    public CurveNode(int id, PipeType type, UUID owner, Vec3 point, BlockPos block, Direction face) {
        this.id = id; this.type = type; this.owner = owner; this.point = point; this.block = block; this.face = face;
    }
    public Direction side() { return face.getOpposite(); }

    /** Reuses Flowline's existing filter/upgrade menu and transfer settings, without placing a block entity. */
    public static final class Endpoint extends PipeBlockEntity {
        private final CurveData data;
        private final CurveNode node;
        public Endpoint(CurveData data, CurveNode node, ServerLevel level) {
            super(node.block.relative(node.face), CurveData.block(node.type).defaultBlockState());
            this.data = data; this.node = node;
            setLevel(level);
        }
        @Override public void setChanged() {
            // Super's constructor/upgrade loading can call this before the owner fields are assigned.
            if (data != null) data.changed();
        }
        @Override public boolean menuValid(net.minecraft.world.entity.player.Player player) {
            return data.nodes().get(node.id) == node && node.active && node.block != null
                    && data.mayEdit(player, node) && super.menuValid(player);
        }
    }
}
