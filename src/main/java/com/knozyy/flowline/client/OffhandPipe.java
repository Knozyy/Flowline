package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.OffhandModePayload;
import com.knozyy.flowline.pipe.OffhandMode;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBuilder;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client side of a pipe in the off hand: the mode key switches between Build for me and Curvy, and in Build for me
 * mode the route that a right-click would lay is shown as ghost pipes, with a short line under the crosshair.
 */
public final class OffhandPipe {
    private static final double CORE = 5 / 16.0;
    private static OffhandMode mode = OffhandMode.BUILD;
    /** Which native Curvy channel a universal pipe lays (index into {@link CurvyPipesCompat#CHANNELS}). */
    private static int channel;
    /** The planned route and what it was planned for; replanned when the target or the player's block changes. */
    private static List<BlockPos> route = List.of();
    @Nullable
    private static BlockPos plannedStart, plannedFeet;
    private static long plannedAt = Long.MIN_VALUE;
    private static Plan plan = Plan.NONE;

    private enum Plan { NONE, NO_TARGET, BLOCKED, READY }

    private OffhandPipe() {}

    /** Curvy mode only exists with Curvy Pipes installed. */
    public static OffhandMode mode() {
        return CurvyPipesCompat.available() ? mode : OffhandMode.BUILD;
    }

    public static int channel() {
        return channel;
    }

    private static boolean universal(Player player) {
        return player != null && CurvyPipesCompat.universal(player.getOffhandItem());
    }

    public static boolean holding(@Nullable Player player) {
        return player != null && CurvyPipesCompat.pipe(player.getOffhandItem());
    }

    /** The mode key: switch modes while a pipe is in the off hand. */
    public static void toggle(Minecraft mc) {
        if (mc.player == null) return;
        if (!holding(mc.player)) {
            mc.player.displayClientMessage(Component.translatable("message.flowline.offhand.no_pipe"), true);
            return;
        }
        if (!CurvyPipesCompat.available()) {
            mc.player.displayClientMessage(Component.translatable("message.flowline.offhand.no_curvy"), true);
            return;
        }
        // a universal pipe has three Curvy channels: Build for me -> Curvy item -> Curvy fluid -> Curvy energy -> back
        if (mode() == OffhandMode.CURVY && universal(mc.player) && channel < CurvyPipesCompat.CHANNELS.size() - 1) {
            channel++;
        } else {
            mode = mode.next();
            channel = 0;
        }
        sync();
        clearRoute();
        mc.player.displayClientMessage(Component.translatable("message.flowline.offhand.switched", modeName()), true);
    }

    /** Tells the server the current mode, e.g. after joining a world. */
    public static void sync() {
        ModNetwork.sendToServer(new OffhandModePayload(mode(), channel));
    }

    private static Component modeName() {
        if (mode() != OffhandMode.CURVY) return Component.translatable("hud.flowline.mode.build");
        Minecraft mc = Minecraft.getInstance();
        if (!universal(mc.player)) return Component.translatable("hud.flowline.mode.curvy");
        return Component.translatable("hud.flowline.mode.curvy_channel",
                Component.translatable("hud.flowline.channel." + CurvyPipesCompat.CHANNELS.get(channel)));
    }

    /** Whether the route preview applies right now: off-hand pipe, Build for me mode, no pipe in the main hand. */
    private static boolean previewing(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.player.isSpectator()
                && holding(mc.player) && mode() == OffhandMode.BUILD
                && !CurvyPipesCompat.pipe(mc.player.getMainHandItem()) && FlowlineConfig.isLoaded();
    }

    private static void clearRoute() {
        route = List.of();
        plannedStart = null;
        plannedFeet = null;
        plan = Plan.NONE;
    }

    public static void tick(Minecraft mc) {
        if (!previewing(mc)) {
            clearRoute();
            return;
        }
        int range = FlowlineConfig.BUILD_RANGE.get();
        HitResult hit = mc.player.pick(range, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            route = List.of();
            plannedStart = null;
            plan = Plan.NO_TARGET;
            return;
        }
        BlockPos start = blockHit.getBlockPos().relative(blockHit.getDirection());
        BlockPos feet = mc.player.blockPosition();
        long now = mc.level.getGameTime();
        if (start.equals(plannedStart) && feet.equals(plannedFeet) && now - plannedAt < 20 && now >= plannedAt) return;
        plannedStart = start;
        plannedFeet = feet;
        plannedAt = now;
        List<BlockPos> path = PipeBuilder.path(mc.level, start, mc.player.getBoundingBox(), range * 3);
        route = path == null ? List.of() : path;
        plan = path == null ? Plan.BLOCKED : Plan.READY;
    }

    private static int accent(ItemStack stack) {
        PipeBlock block = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof PipeBlock pipe ? pipe
                : CurvyPipesCompat.block(stack.getItem());
        return Theme.accent(block == null ? PipeType.ITEM : block.type());
    }

    /** Ghost pipes along the route: a core per block and arms joining it to its neighbours on the route. */
    public static void render(PoseStack pose, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (route.isEmpty() || !previewing(mc)) return;
        int colour = accent(mc.player.getOffhandItem());
        float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
        int affordable = mc.player.getAbilities().instabuild ? route.size()
                : Math.min(route.size(), mc.player.getOffhandItem().getCount());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        for (int i = 0; i < route.size(); i++) {
            float alpha = i < affordable ? 0.38f : 0.12f;
            for (AABB box : pieces(i)) {
                LevelRenderer.addChainedFilledBoxVertices(pose, fill, box.minX, box.minY, box.minZ, box.maxX, box.maxY,
                        box.maxZ, r, g, b, alpha);
            }
        }
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (int i = 0; i < route.size(); i++) {
            LevelRenderer.renderLineBox(pose, lines, core(route.get(i)), r, g, b, i < affordable ? 0.9f : 0.35f);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static AABB core(BlockPos pos) {
        return new AABB(pos).deflate(CORE);
    }

    /** The core of route position {@code i} and the arms reaching towards the previous and next position. */
    private static List<AABB> pieces(int i) {
        BlockPos pos = route.get(i);
        List<AABB> boxes = new java.util.ArrayList<>();
        boxes.add(core(pos));
        for (int j : new int[]{i - 1, i + 1}) {
            if (j < 0 || j >= route.size()) continue;
            Direction dir = null;
            for (Direction d : Direction.values()) {
                if (pos.relative(d).equals(route.get(j))) dir = d;
            }
            if (dir == null) continue;
            AABB core = core(pos);
            double lo = 0, hi = 1;
            boxes.add(switch (dir.getAxis()) {
                case X -> new AABB(dir == Direction.EAST ? core.maxX : pos.getX() + lo, core.minY, core.minZ,
                        dir == Direction.EAST ? pos.getX() + hi : core.minX, core.maxY, core.maxZ);
                case Y -> new AABB(core.minX, dir == Direction.UP ? core.maxY : pos.getY() + lo, core.minZ,
                        core.maxX, dir == Direction.UP ? pos.getY() + hi : core.minY, core.maxZ);
                case Z -> new AABB(core.minX, core.minY, dir == Direction.SOUTH ? core.maxZ : pos.getZ() + lo,
                        core.maxX, core.maxY, dir == Direction.SOUTH ? pos.getZ() + hi : core.minZ);
            });
        }
        return boxes;
    }

    /** A line under the crosshair: the mode, what a right-click would do, and the mode key. */
    public static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !holding(mc.player)
                || CurvyPipesCompat.pipe(mc.player.getMainHandItem()) || !FlowlineConfig.isLoaded()) return;
        Component main;
        if (mode() == OffhandMode.CURVY) {
            main = Component.translatable("hud.flowline.curvy.ready");
        } else {
            int have = mc.player.getAbilities().instabuild ? Integer.MAX_VALUE : mc.player.getOffhandItem().getCount();
            main = switch (plan) {
                case READY -> have >= route.size()
                        ? Component.translatable("hud.flowline.build.route", route.size())
                        : Component.translatable("hud.flowline.build.short", route.size(), have);
                case BLOCKED -> Component.translatable("hud.flowline.build.no_route");
                default -> Component.translatable("hud.flowline.build.aim", FlowlineConfig.BUILD_RANGE.get());
            };
        }
        Component key = Component.translatable("hud.flowline.mode.key", Keys.BUILD.getTranslatedKeyMessage());
        Component name = modeName();
        int nameW = mc.font.width(name) + 8;
        int width = nameW + 6 + Math.max(mc.font.width(main), mc.font.width(key));
        int x = (g.guiWidth() - width) / 2, y = g.guiHeight() / 2 + 14;
        int accent = 0xFF000000 | accent(mc.player.getOffhandItem());
        g.fill(x - 5, y - 4, x + width + 5, y + 22, 0xC8000000 | (Theme.BG & 0xFFFFFF));
        g.fill(x, y - 1, x + nameW, y + 10, accent);
        g.drawString(mc.font, name, x + 4, y + 1, Theme.ON_ACCENT, false);
        g.drawString(mc.font, main, x + nameW + 6, y + 1, Theme.TEXT, false);
        g.drawString(mc.font, key, x + nameW + 6, y + 12, Theme.MUTED, false);
    }
}
