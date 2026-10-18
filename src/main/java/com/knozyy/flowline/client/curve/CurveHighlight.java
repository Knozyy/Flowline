package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.Obb;
import com.knozyy.flowline.curve.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Keeps {@link #current} up to date each tick and outlines the node or edge it names, in place of the block outline. */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class CurveHighlight {
    @Nullable
    private static CurveTarget current;

    private CurveHighlight() {}

    @Nullable
    public static CurveTarget current() {
        return current;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            current = null;
            return;
        }
        if (ClientCurves.INSTANCE.graph().nodes().isEmpty() && !CurvePlacer.active(mc)) {
            current = null;
            return;
        }
        Vec3 eye = mc.player.getEyePosition(1f), look = mc.player.getViewVector(1f);
        current = CurveTarget.find(mc.level, ClientCurves.INSTANCE, mc.player, new Vec3d(eye.x, eye.y, eye.z),
                new Vec3d(look.x, look.y, look.z), mc.player.getBlockReach(), CurvePlacer.grid(), CurvePlacer.snapping());
    }

    private static boolean outlinesCurve() {
        return current != null && (current.kind == CurveTarget.Kind.NODE || current.kind == CurveTarget.Kind.EDGE);
    }

    /** A node or edge in front of the block takes the outline from it. */
    @SubscribeEvent
    public static void hideBlockOutline(RenderHighlightEvent.Block event) {
        if (outlinesCurve()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !outlinesCurve()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (current.kind == CurveTarget.Kind.NODE) {
            double r = CurveTarget.NODE_PICK;
            Vec3d p = current.pos;
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, lines, new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r),
                    0, 0, 0, 0.4f);
            pose.popPose();
        } else {
            Vec3d origin = new Vec3d(cam.x, cam.y, cam.z);
            for (Obb box : ClientCurves.INSTANCE.graph().boxes(current.edgeId)) obb(pose, lines, origin, box);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** The twelve edges of a box, relative to the camera. */
    private static void obb(PoseStack pose, VertexConsumer out, Vec3d origin, Obb box) {
        Vec3d[] corners = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = box.center().sub(origin)
                    .addScaled(box.u(), (i & 1) == 0 ? -box.hu() : box.hu())
                    .addScaled(box.v(), (i & 2) == 0 ? -box.hv() : box.hv())
                    .addScaled(box.w(), (i & 4) == 0 ? -box.hw() : box.hw());
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                if ((i & bit) != 0) continue;
                Vec3d a = corners[i], b = corners[i | bit], d = b.sub(a).normalize();
                out.vertex(m, (float) a.x(), (float) a.y(), (float) a.z()).color(0, 0, 0, 0.4f)
                        .normal(n, (float) d.x(), (float) d.y(), (float) d.z()).endVertex();
                out.vertex(m, (float) b.x(), (float) b.y(), (float) b.z()).color(0, 0, 0, 0.4f)
                        .normal(n, (float) d.x(), (float) d.y(), (float) d.z()).endVertex();
            }
        }
    }
}
