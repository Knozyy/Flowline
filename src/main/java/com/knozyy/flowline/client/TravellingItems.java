package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.network.TravelPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side, purely visual copies of items the server moved: each glides from the source block through the pipe
 * centres to the target block, then disappears. The real transfer already happened.
 */
public final class TravellingItems {
    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static ClientLevel currentLevel;

    private record Entry(ItemStack stack, List<Vec3> points, long start, int duration, float scale) {}

    private TravellingItems() {}

    public static void add(TravelPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !FlowlineConfig.Client.RENDER_ITEMS.get() || payload.path().size() < 2) return;
        tick(mc.level);
        int max = FlowlineConfig.Client.MAX_TRAVELLING.get();
        if (max <= 0) return;
        List<Vec3> points = new ArrayList<>(payload.path().size());
        List<BlockPos> path = payload.path();
        for (int i = 0; i < path.size(); i++) {
            Vec3 center = Vec3.atCenterOf(path.get(i));
            // start and end at the face between the block and the pipe, not inside the block
            if (i == 0) center = center.lerp(Vec3.atCenterOf(path.get(1)), 0.5);
            if (i == path.size() - 1) center = center.lerp(Vec3.atCenterOf(path.get(i - 1)), 0.5);
            points.add(center);
        }
        int duration = Math.max(1, (points.size() - 1) * FlowlineConfig.Client.TICKS_PER_PIPE.get());
        while (ENTRIES.size() >= max) ENTRIES.pollFirst();
        ENTRIES.add(new Entry(payload.stack(), points, mc.level.getGameTime(), duration, 0.35f));
    }

    public static void tick(ClientLevel level) {
        if (currentLevel != level) { clear(); currentLevel = level; }
        if (!FlowlineConfig.Client.RENDER_ITEMS.get()) { ENTRIES.clear(); return; }
        long now = level.getGameTime();
        ENTRIES.removeIf(e -> now - e.start() > e.duration() || now < e.start());
    }

    public static void clear() {
        ENTRIES.clear();
        currentLevel = null;
    }

    public static void render(PoseStack pose, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { clear(); return; }
        tick(level);
        if (ENTRIES.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double now = level.getGameTime() + partialTick;
        Iterator<Entry> it = ENTRIES.iterator();
        int seed = 0;
        while (it.hasNext()) {
            Entry e = it.next();
            double t = Math.max(0, Math.min(1, (now - e.start()) / e.duration()));
            Vec3 pos = along(e.points(), t);
            if (pos.distanceToSqr(camera) > 64 * 64) continue;
            pose.pushPose();
            pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
            pose.scale(e.scale(), e.scale(), e.scale());
            pose.mulPose(Axis.YP.rotationDegrees((float) ((now * 4) % 360)));
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(pos));
            mc.getItemRenderer().renderStatic(e.stack(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, seed++);
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Point at fraction {@code t} of the polyline, every segment taking the same time. */
    private static Vec3 along(List<Vec3> points, double t) {
        int segments = points.size() - 1;
        double scaled = t * segments;
        int i = Math.min(segments - 1, (int) scaled);
        return points.get(i).lerp(points.get(i + 1), scaled - i);
    }
}
