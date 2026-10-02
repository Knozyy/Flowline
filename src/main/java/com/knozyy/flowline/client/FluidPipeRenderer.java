package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class FluidPipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    private static final double MIN = 6 / 16.0, MAX = 10 / 16.0;

    public FluidPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRender(PipeBlockEntity pipe, Vec3 camera) {
        int range = FlowlineConfig.Client.FLUID_RENDER_RANGE.get();
        return pipe.type() == PipeType.FLUID && pipe.facade() == null && FlowlineConfig.Client.RENDER_FLUIDS.get()
                && FlowingFluids.at(pipe.getBlockPos()) != null
                && Vec3.atCenterOf(pipe.getBlockPos()).distanceToSqr(camera) <= (double) range * range;
    }

    @Override
    public int getViewDistance() { return FlowlineConfig.Client.FLUID_RENDER_RANGE.get(); }

    @Override
    public void render(PipeBlockEntity pipe, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        FlowingFluids.Flow flow = FlowingFluids.at(pipe.getBlockPos());
        if (flow == null || pipe.getLevel() == null || !FlowlineConfig.Client.RENDER_FLUIDS.get()) return;
        double now = pipe.getLevel().getGameTime() + partialTick;
        float fade = (float) Math.min(1, Math.max(0, (flow.expires() - now) / 8));
        if (fade <= 0) return;
        FluidStack fluid = new FluidStack(flow.fluid(), 1000);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(flow.fluid());
        var texture = ext.getStillTexture(fluid);
        if (texture == null) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        int tint = ext.getTintColor(fluid);
        float alpha = ((tint >>> 24) & 255) / 255f;
        float[] colour = {((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f,
                (tint & 255) / 255f, (alpha == 0 ? 1 : alpha) * fade};
        int emission = fluid.getFluid().getFluidType().getLightLevel(fluid);
        int packedLight = LightTexture.pack(Math.max(LightTexture.block(light), emission), LightTexture.sky(light));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        int connected = 0;
        for (Direction side : Direction.values()) {
            if ((flow.connections() & 1 << side.ordinal()) != 0
                    && pipe.getBlockState().getValue(PipeBlock.prop(side)) != Conn.NONE) connected |= 1 << side.ordinal();
        }
        box(pose, vertices, sprite, colour, packedLight, now, flow.direction(), connected, MIN, MIN, MIN, MAX, MAX, MAX);
        for (Direction side : Direction.values()) {
            if ((flow.connections() & 1 << side.ordinal()) == 0
                    || pipe.getBlockState().getValue(PipeBlock.prop(side)) == Conn.NONE) continue;
            Direction movement = (flow.outgoing() & 1 << side.ordinal()) != 0 ? side : side.getOpposite();
            double x0 = MIN, y0 = MIN, z0 = MIN, x1 = MAX, y1 = MAX, z1 = MAX;
            switch (side) {
                case DOWN -> { y0 = 0; y1 = MIN; }
                case UP -> { y0 = MAX; y1 = 1; }
                case NORTH -> { z0 = 0; z1 = MIN; }
                case SOUTH -> { z0 = MAX; z1 = 1; }
                case WEST -> { x0 = 0; x1 = MIN; }
                case EAST -> { x0 = MAX; x1 = 1; }
            }
            int hidden = 1 << side.getOpposite().ordinal();
            FlowingFluids.Flow neighbour = FlowingFluids.at(pipe.getBlockPos().relative(side));
            if (pipe.getBlockState().getValue(PipeBlock.prop(side)) == Conn.PIPE && neighbour != null
                    && neighbour.fluid() == flow.fluid() && neighbour.expires() > now
                    && (neighbour.connections() & 1 << side.getOpposite().ordinal()) != 0) hidden |= 1 << side.ordinal();
            box(pose, vertices, sprite, colour, packedLight, now, movement, hidden, x0, y0, z0, x1, y1, z1);
        }
    }

    private static void box(PoseStack pose, VertexConsumer vertices, TextureAtlasSprite sprite, float[] colour,
                            int light, double now, Direction flow, int hidden, double x0, double y0, double z0,
                            double x1, double y1, double z1) {
        if ((hidden & 1 << Direction.UP.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.UP,
                new Vec3(x0,y1,z0), new Vec3(x0,y1,z1), new Vec3(x1,y1,z1), new Vec3(x1,y1,z0));
        if ((hidden & 1 << Direction.DOWN.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.DOWN,
                new Vec3(x0,y0,z1), new Vec3(x0,y0,z0), new Vec3(x1,y0,z0), new Vec3(x1,y0,z1));
        if ((hidden & 1 << Direction.NORTH.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.NORTH,
                new Vec3(x1,y0,z0), new Vec3(x0,y0,z0), new Vec3(x0,y1,z0), new Vec3(x1,y1,z0));
        if ((hidden & 1 << Direction.SOUTH.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.SOUTH,
                new Vec3(x0,y0,z1), new Vec3(x1,y0,z1), new Vec3(x1,y1,z1), new Vec3(x0,y1,z1));
        if ((hidden & 1 << Direction.WEST.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.WEST,
                new Vec3(x0,y0,z0), new Vec3(x0,y0,z1), new Vec3(x0,y1,z1), new Vec3(x0,y1,z0));
        if ((hidden & 1 << Direction.EAST.ordinal()) == 0) face(pose, vertices, sprite, colour, light, now, flow, Direction.EAST,
                new Vec3(x1,y0,z1), new Vec3(x1,y0,z0), new Vec3(x1,y1,z0), new Vec3(x1,y1,z1));
    }

    private static void face(PoseStack pose, VertexConsumer vertices, TextureAtlasSprite sprite, float[] colour,
                             int light, double now, Direction flow, Direction normal, Vec3... corners) {
        if (normal.getAxis() == flow.getAxis()) {
            quad(pose, vertices, sprite, colour, light, normal, corners[0], corners[1], corners[2], corners[3], 0, 1);
            return;
        }
        Vec3 forward = Vec3.atLowerCornerOf(flow.getNormal());
        int start = 0;
        while (start < 3 && corners[(start + 1) % 4].subtract(corners[start]).dot(forward) <= 0) start++;
        Vec3 a = corners[start], b = corners[(start + 1) % 4], c = corners[(start + 2) % 4], d = corners[(start + 3) % 4];
        double span = b.subtract(a).length() * 2;
        double phase = a.dot(forward) * 2 - now * 0.06;
        phase -= Math.floor(phase);
        double offset = 0;
        while (offset < span - 0.00001) {
            double length = Math.min(span - offset, 1 - phase);
            double from = offset / span, to = (offset + length) / span;
            quad(pose, vertices, sprite, colour, light, normal, a.lerp(b, from), a.lerp(b, to), d.lerp(c, to), d.lerp(c, from),
                    phase, phase + length);
            offset += length;
            phase = 0;
        }
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, TextureAtlasSprite sprite, float[] colour,
                             int light, Direction normal, Vec3 a, Vec3 b, Vec3 c, Vec3 d, double v0, double v1) {
        vertex(pose, vertices, colour, light, normal, a, sprite.getU0(), sprite.getV(v0 * 16));
        vertex(pose, vertices, colour, light, normal, b, sprite.getU0(), sprite.getV(v1 * 16));
        vertex(pose, vertices, colour, light, normal, c, sprite.getU1(), sprite.getV(v1 * 16));
        vertex(pose, vertices, colour, light, normal, d, sprite.getU1(), sprite.getV(v0 * 16));
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, float[] colour, int light, Direction normal,
                               Vec3 pos, float u, float v) {
        vertices.vertex(pose.last().pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .color(colour[0], colour[1], colour[2], colour[3]).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.last().normal(), normal.getStepX(), normal.getStepY(), normal.getStepZ()).endVertex();
    }
}
