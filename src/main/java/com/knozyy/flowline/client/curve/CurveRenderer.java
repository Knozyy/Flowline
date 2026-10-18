package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.curve.Aabb;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curve.MeshPart;
import com.knozyy.flowline.curve.MeshQuad;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the curved pipes the client knows after the translucent blocks: rails, collars and bands solid on the block
 * atlas, glass translucent with back faces culled (the inner glass faces are their own quads). Edges out of view are
 * skipped; meshes come from {@link MeshCache}.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class CurveRenderer {
    public static final MeshCache CACHE = new MeshCache(ClientCurves.INSTANCE);
    private static final ResourceLocation RAIL = new ResourceLocation(Flowline.MODID, "block/pipe_rail");
    private static int seenVersion = -1;

    private CurveRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientCurves curves = ClientCurves.INSTANCE;
        if (curves.version() != seenVersion) {
            seenVersion = curves.version();
            CACHE.prune();
        }
        if (curves.graph().edges().isEmpty()) return;
        Camera camera = event.getCamera();
        Frustum frustum = event.getFrustum();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        Vec3d origin = new Vec3d(cam.x, cam.y, cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType solid = RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS);
        RenderType glass = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
        for (CurveEdge edge : curves.graph().edges()) {
            CurveEdgesPayload.EdgeSync sync = curves.edge(edge.id());
            Aabb bounds = CACHE.bounds(edge.id());
            if (sync == null || bounds == null || !frustum.isVisible(aabb(bounds))) continue;
            draw(pose, buffers, solid, glass, mc.level, origin, CACHE.edge(edge.id()), sync.type(),
                    railColor(sync));
        }
        for (CurveNode node : curves.graph().nodes()) {
            if (!CACHE.isJoint(node.id())) continue;
            List<CurveEdge> at = curves.graph().edgesAt(node.id());
            CurveEdgesPayload.EdgeSync first = at.isEmpty() ? null : curves.edge(at.get(0).id());
            if (first == null) continue;
            draw(pose, buffers, solid, glass, mc.level, origin, CACHE.joint(node.id()), first.type(),
                    railColor(first));
        }
        buffers.endBatch(solid);
        buffers.endBatch(glass);
    }

    /** The rails' tint: the dye colour, or the pipe type's own colour when undyed. */
    static int railColor(CurveEdgesPayload.EdgeSync sync) {
        if (sync.color() == PipeBlockEntity.NO_COLOR) return Theme.accent(sync.type()) & 0xFFFFFF;
        float[] rgb = DyeColor.byId(sync.color()).getTextureDiffuseColors();
        return (int) (rgb[0] * 255) << 16 | (int) (rgb[1] * 255) << 8 | (int) (rgb[2] * 255);
    }

    static AABB aabb(Aabb b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    static ResourceLocation texture(PipeType type, MeshPart part) {
        String name = type.getSerializedName() + "_pipe";
        return switch (part) {
            case RAIL -> RAIL;
            case GLASS, GLASS_INNER, NODE_GLASS -> new ResourceLocation(Flowline.MODID, "block/" + name + "_glass");
            case COLLAR -> new ResourceLocation(Flowline.MODID, "block/" + name + "_collar");
            case RING -> new ResourceLocation(Flowline.MODID, "block/" + name + "_extract");
        };
    }

    /**
     * Draws world-space quads relative to {@code origin} (the camera; subtracting in double keeps far pipes steady);
     * {@code tint} colours the rails.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, RenderType solid, RenderType glass, Level level,
                     Vec3d origin, List<MeshQuad> quads, PipeType type, int tint) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer solidOut = buffers.getBuffer(solid);
        VertexConsumer glassOut = buffers.getBuffer(glass);
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[MeshPart.values().length];
        for (MeshQuad q : quads) {
            MeshPart part = q.part();
            if (sprites[part.ordinal()] == null) sprites[part.ordinal()] = sprite(texture(type, part));
            TextureAtlasSprite s = sprites[part.ordinal()];
            boolean isGlass = part == MeshPart.GLASS || part == MeshPart.GLASS_INNER || part == MeshPart.NODE_GLASS;
            int rgb = part == MeshPart.RAIL ? tint : 0xFFFFFF;
            Vec3d c = q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(c.x(), c.y(), c.z()));
            quad(isGlass ? glassOut : solidOut, matrix, normals, origin, q, s, rgb, 255, light);
        }
    }

    static void quad(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3d origin, MeshQuad q,
                     TextureAtlasSprite s, int rgb, int alpha, int light) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float u0 = s.getU(q.u0()), u1 = s.getU(q.u1()), v0 = s.getV(q.v0()), v1 = s.getV(q.v1());
        vertex(out, matrix, normals, q.a().sub(origin), q.normal(), r, g, b, alpha, u0, v0, light);
        vertex(out, matrix, normals, q.b().sub(origin), q.normal(), r, g, b, alpha, u1, v0, light);
        vertex(out, matrix, normals, q.c().sub(origin), q.normal(), r, g, b, alpha, u1, v1, light);
        vertex(out, matrix, normals, q.d().sub(origin), q.normal(), r, g, b, alpha, u0, v1, light);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3d p, Vec3d n, int r, int g,
                               int b, int a, float u, float v, int light) {
        out.vertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) n.x(), (float) n.y(), (float) n.z()).endVertex();
    }
}
