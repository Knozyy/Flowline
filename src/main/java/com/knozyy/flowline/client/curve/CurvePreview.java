package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.curve.ChainPreview;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveGraph;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.FaceAttachment;
import com.knozyy.flowline.curve.MeshPart;
import com.knozyy.flowline.curve.MeshQuad;
import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.curve.Reach;
import com.knozyy.flowline.curve.SolvedSpan;
import com.knozyy.flowline.curve.TubeMesh;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.PlaceRequest;
import com.knozyy.flowline.curved.WorldShapes;
import com.knozyy.flowline.pipe.PipeBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The half-drawn chain and the pointed-at end, solved and checked on the client: a ghost tube in the pipe's colour,
 * red when the client already sees a problem, a red line when there is no shape at all. Only a preview; the server
 * decides when the chain is sent.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class CurvePreview {
    private static final int BAD = 0xE04848;
    @Nullable
    private static ChainPreview preview;
    private static List<Vec3d> points = List.of();
    private static Optional<PlaceError> localError = Optional.empty();

    private CurvePreview() {}

    @Nullable
    public static ChainPreview current() {
        return preview;
    }

    /** The first problem the client sees: out of reach, or what the solver and validator say. */
    public static Optional<PlaceError> localError() {
        return localError;
    }

    /** Works the preview out again when the chain or the pointed-at end moved; called after the target is found. */
    static void update(Minecraft mc, @Nullable CurveTarget target) {
        if (mc.player == null || mc.level == null || !CurvePlacer.drawing() || target == null) {
            preview = null;
            points = List.of();
            localError = Optional.empty();
            return;
        }
        ClientCurves curves = ClientCurves.INSTANCE;
        ChainPreview.End start = resolve(mc.level, curves.graph(), CurvePlacer.start(), true);
        ChainPreview.End end = resolve(mc.level, curves.graph(), CurvePlacer.end(target), false);
        List<Vec3d> now = new ArrayList<>();
        now.add(start.pos());
        now.addAll(CurvePlacer.internals());
        now.add(end.pos());
        if (now.equals(points) && preview != null) return;
        points = List.copyOf(now);
        preview = ChainPreview.of(curves.graph(), new WorldShapes(mc.level), start, CurvePlacer.internals(), end);
        Vec3 eye = mc.player.getEyePosition();
        Vec3d at = new Vec3d(eye.x, eye.y, eye.z);
        boolean far = now.stream().anyMatch(p -> Reach.tooFar(at, mc.player.getBlockReach(), p));
        localError = far ? Optional.of(PlaceError.TOO_FAR) : preview.error();
    }

    /** A chain end as the server will read it (CurveEdits.resolve), from what the client knows. */
    static ChainPreview.End resolve(Level level, CurveGraph graph, PlaceRequest.End end, boolean isStart) {
        Vec3d pos = new Vec3d(end.pos().x, end.pos().y, end.pos().z);
        if (end.split().isPresent()) {
            PlaceRequest.Split split = end.split().get();
            CurveEdge edge = graph.edge(split.edge()).orElse(null);
            if (edge != null && split.segment() >= 0 && split.segment() + 1 < edge.shape().points().size()) {
                Vec3d a = edge.shape().points().get(split.segment()), b = edge.shape().points().get(split.segment() + 1);
                return new ChainPreview.End(a.add(b.sub(a).scale(split.along())), -1, EndCondition.FREE, null, edge.id());
            }
        }
        if (end.node().isPresent()) {
            long id = end.node().getAsLong();
            CurveNode node = graph.node(id).orElse(null);
            if (node == null) return new ChainPreview.End(pos, -1, EndCondition.FREE, null);
            List<CurveEdge> edges = graph.edgesAt(id);
            EndCondition condition = EndCondition.FREE;
            if (edges.size() == 1 && !node.forceJoint()) {
                Vec3d exit = CurveGraph.exitDirection(edges.get(0), id);
                condition = new EndCondition(isStart ? exit.scale(-1) : exit);
            }
            return new ChainPreview.End(node.pos(), id, condition, null);
        }
        if (end.face().isPresent()) {
            PlaceRequest.Face face = end.face().get();
            Direction dir = face.dir();
            Vec3d normal = new Vec3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            EndCondition condition = new EndCondition(isStart ? normal : normal.scale(-1));
            if (level.getBlockState(face.pos()).getBlock() instanceof PipeBlock) {
                Vec3d centre = new Vec3d(face.pos().getX() + 0.5 + 0.5 * normal.x(), face.pos().getY() + 0.5 + 0.5 * normal.y(),
                        face.pos().getZ() + 0.5 + 0.5 * normal.z());
                return new ChainPreview.End(centre, -1, condition, null);
            }
            return new ChainPreview.End(pos, -1, condition,
                    new FaceAttachment(face.pos().getX(), face.pos().getY(), face.pos().getZ(), dir.ordinal()));
        }
        return new ChainPreview.End(pos, -1, EndCondition.FREE, null);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !CurveInput.active(mc)) return;
        Vec3 cam = event.getCamera().getPosition();
        Vec3d origin = new Vec3d(cam.x, cam.y, cam.z);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        CurveTarget target = CurveHighlight.current();
        boolean bad = localError.isPresent();
        int colour = bad ? BAD : Theme.accent(CurvePlacer.drawingType()) & 0xFFFFFF;
        if (preview != null && !preview.spans().isEmpty()) {
            RenderType ghost = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
            VertexConsumer out = buffers.getBuffer(ghost);
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            for (SolvedSpan span : preview.spans()) {
                for (MeshQuad q : TubeMesh.edge(span.points(), TubeMesh.EndStyle.NONE, TubeMesh.EndStyle.NONE)) {
                    if (q.part() == MeshPart.GLASS_INNER) continue;
                    TextureAtlasSprite s = CurveRenderer.sprite(CurveRenderer.texture(CurvePlacer.drawingType(), q.part()));
                    CurveRenderer.quad(out, m, n, origin, q, s, colour, 150, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                }
            }
            buffers.endBatch(ghost);
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (preview != null && preview.spans().isEmpty()) {
            for (int i = 0; i + 1 < points.size(); i++) line(pose, lines, origin, points.get(i), points.get(i + 1), BAD);
        }
        if (target != null) {
            Vec3d p = target.pos.sub(origin);
            double r = 0.06;
            LevelRenderer.renderLineBox(pose, lines, new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r),
                    (colour >> 16 & 255) / 255f, (colour >> 8 & 255) / 255f, (colour & 255) / 255f, 1f);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void line(PoseStack pose, VertexConsumer out, Vec3d origin, Vec3d a, Vec3d b, int rgb) {
        Vec3d p = a.sub(origin), q = b.sub(origin), d = b.sub(a).normalize();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        out.vertex(m, (float) p.x(), (float) p.y(), (float) p.z()).color(r, g, bl, 255)
                .normal(n, (float) d.x(), (float) d.y(), (float) d.z()).endVertex();
        out.vertex(m, (float) q.x(), (float) q.y(), (float) q.z()).color(r, g, bl, 255)
                .normal(n, (float) d.x(), (float) d.y(), (float) d.z()).endVertex();
    }
}
