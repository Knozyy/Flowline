package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.NetworkQueryPayload;
import com.knozyy.flowline.network.NetworkViewPayload;
import com.knozyy.flowline.pipe.NetworkView;
import com.knozyy.flowline.pipe.PipeBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.OptionalDouble;

public final class NetworkOverlay {
    private static final int[] COLOURS = {0xC6D4E5, 0x66E38D, 0xFFA94D, 0xBF86FF};
    private static ClientLevel level;
    private static BlockPos aimed;
    private static long lastQuery = Long.MIN_VALUE, received;
    private static NetworkView.Snapshot snapshot = new NetworkView.Snapshot(List.of(), false);

    private NetworkOverlay() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        BlockPos pos = lookedAt(mc);
        if (pos == null || !pos.equals(aimed)) {
            aimed = pos;
            snapshot = new NetworkView.Snapshot(List.of(), false);
        }
        if (pos == null) return;
        long now = mc.level.getGameTime();
        if (lastQuery == Long.MIN_VALUE || now < lastQuery || now - lastQuery >= 20) {
            lastQuery = now;
            ModNetwork.sendToServer(new NetworkQueryPayload(pos));
        }
    }

    private static BlockPos lookedAt(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown()
                || !FlowlineConfig.Client.RENDER_NETWORK_VIEW.get() || !FlowlineConfig.isLoaded()
                || !FlowlineConfig.ALLOW_NETWORK_VIEW.get() || !WrenchItem.isWrench(mc.player.getMainHandItem())) return null;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof PipeBlock ? hit.getBlockPos() : null;
    }

    public static void receive(NetworkViewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level || !mc.level.dimension().location().equals(payload.dimension())
                || !payload.origin().equals(aimed) || !payload.origin().equals(lookedAt(mc))) return;
        snapshot = payload.view();
        received = mc.level.getGameTime();
    }

    private static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level == level && aimed != null && aimed.equals(lookedAt(mc))
                && !snapshot.entries().isEmpty() && mc.level.getGameTime() - received <= 40;
    }

    public static void clear() {
        level = null;
        aimed = null;
        lastQuery = Long.MIN_VALUE;
        snapshot = new NetworkView.Snapshot(List.of(), false);
    }

    public static void render(PoseStack pose, Vec3 camera) {
        if (!visible()) return;
        Minecraft mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(Lines.TYPE);
        int min = snapshot.entries().stream().filter(e -> e.has(NetworkView.TARGET) || e.has(NetworkView.OVERFLOW))
                .mapToInt(NetworkView.Entry::priority).min().orElse(0);
        int max = snapshot.entries().stream().filter(e -> e.has(NetworkView.TARGET) || e.has(NetworkView.OVERFLOW))
                .mapToInt(NetworkView.Entry::priority).max().orElse(0);
        int range = FlowlineConfig.NETWORK_VIEW_RANGE.get();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (NetworkView.Entry entry : snapshot.entries()) {
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(mc.player.position()) > (double) range * range) continue;
            int colour = COLOURS[entry.has(NetworkView.SOURCE) ? 1 : entry.has(NetworkView.TARGET) ? 2
                    : entry.has(NetworkView.OVERFLOW) ? 3 : 0];
            boolean endpoint = !entry.has(NetworkView.PIPE);
            float brightness = endpoint && max != min
                    ? (float) (0.55 + 0.45 * ((double) entry.priority() - min) / ((double) max - min)) : 1;
            if (entry.has(NetworkView.SOURCE)) brightness = 1;
            AABB box = new AABB(entry.pos()).inflate(0.005);
            if (!endpoint) box = new AABB(entry.pos()).deflate(0.18);
            LevelRenderer.renderLineBox(pose, vertices, box, ((colour >> 16) & 255) / 255f * brightness,
                    ((colour >> 8) & 255) / 255f * brightness, (colour & 255) / 255f * brightness,
                    endpoint ? 1 : 0.45f);
        }
        pose.popPose();
        buffers.endBatch(Lines.TYPE);
    }

    public static void legend(GuiGraphics graphics) {
        if (!visible() || Minecraft.getInstance().options.hideGui) return;
        Minecraft mc = Minecraft.getInstance();
        String[] keys = {"pipes", "sources", "targets", "overflow"};
        for (int i = 0; i < keys.length; i++) {
            int y = 8 + i * 12;
            Component label = Component.translatable("gui.flowline.network." + keys[i]);
            graphics.fill(6, y - 1, 23 + mc.font.width(label), y + 10, 0xA0000000);
            graphics.fill(9, y + 2, 15, y + 8, 0xFF000000 | COLOURS[i]);
            graphics.drawString(mc.font, label, 19, y, 0xFFFFFF);
        }
        if (snapshot.truncated()) graphics.drawString(mc.font, Component.translatable("gui.flowline.network.truncated"),
                8, 58, 0xFFFFFF);
    }

    private static final class Lines extends RenderStateShard {
        private Lines() { super("flowline_network", () -> {}, () -> {}); }

        private static final RenderType TYPE = RenderType.create("flowline_network", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
}
