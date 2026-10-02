package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.network.FluidFlowPayload;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowingFluids {
    public record Flow(Fluid fluid, int connections, int outgoing, Direction direction, long expires) {}

    private static final Map<BlockPos, Flow> FLOWS = new LinkedHashMap<>();
    private static ClientLevel level;

    private FlowingFluids() {}

    public static void add(FluidFlowPayload payload) {
        tick();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || !level.dimension().location().equals(payload.dimension())
                || !FlowlineConfig.Client.RENDER_FLUIDS.get() || mc.player == null) return;
        int max = FlowlineConfig.Client.MAX_FLUID_PIPES.get();
        if (max == 0) return;
        int range = FlowlineConfig.Client.FLUID_RENDER_RANGE.get();
        List<BlockPos> path = payload.path();
        long expires = level.getGameTime() + 20;
        for (int i = 1; i + 1 < path.size(); i++) {
            BlockPos pos = path.get(i);
            if (Vec3.atCenterOf(pos).distanceToSqr(mc.player.position()) > (double) range * range
                    || !level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof PipeBlock pipe)
                    || pipe.type() != PipeType.FLUID) continue;
            Direction incoming = direction(pos, path.get(i - 1));
            Direction outgoing = direction(pos, path.get(i + 1));
            if (incoming == null || outgoing == null) continue;
            int mask = 1 << incoming.ordinal() | 1 << outgoing.ordinal();
            int outputs = 1 << outgoing.ordinal();
            Flow old = FLOWS.remove(pos);
            if (old != null && old.fluid() == payload.fluid() && old.expires() == expires) {
                mask |= old.connections();
                outputs |= old.outgoing();
            }
            while (FLOWS.size() >= max) FLOWS.remove(FLOWS.keySet().iterator().next());
            FLOWS.put(pos.immutable(), new Flow(payload.fluid(), mask, outputs, outgoing, expires));
        }
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        for (Direction dir : Direction.values()) if (from.relative(dir).equals(to)) return dir;
        return null;
    }

    public static Flow at(BlockPos pos) { return FLOWS.get(pos); }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        if (level == null || !FlowlineConfig.Client.RENDER_FLUIDS.get()) { FLOWS.clear(); return; }
        long now = level.getGameTime();
        FLOWS.entrySet().removeIf(e -> e.getValue().expires() <= now || e.getValue().expires() > now + 20
                || !level.hasChunkAt(e.getKey()));
        int max = FlowlineConfig.Client.MAX_FLUID_PIPES.get();
        while (FLOWS.size() > max) FLOWS.remove(FLOWS.keySet().iterator().next());
    }

    public static void clear() { FLOWS.clear(); level = null; }
}
