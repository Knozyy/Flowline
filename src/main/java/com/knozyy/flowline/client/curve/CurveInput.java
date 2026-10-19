package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.Keys;
import com.knozyy.flowline.client.OffhandPipe;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.curve.CurveBreakPayload;
import com.knozyy.flowline.network.curve.CurveEndPayload;
import com.knozyy.flowline.network.curve.CurvePaintPayload;
import com.knozyy.flowline.network.curve.CurveRemovePayload;
import com.knozyy.flowline.network.curve.CurveTranslatePayload;
import com.knozyy.flowline.pipe.OffhandMode;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the mouse and keys into curved pipe actions ({@link CurvePlacer#decide}) through Forge's
 * {@code InteractionKeyMappingTriggered}, so no mixin is needed: a claimed click is cancelled and vanilla never places,
 * breaks or picks a block for it.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class CurveInput {
    private static long lastBreak = CurvePlacer.NEVER;

    private CurveInput() {}

    /** Whether curved placement is in hand: a pipe in the off hand in curved mode, none in the main hand. */
    public static boolean active(Minecraft mc) {
        return mc.player != null && OffhandPipe.holding(mc.player) && OffhandPipe.mode() == OffhandMode.CURVY
                && !PipeBlock.isPipe(mc.player.getMainHandItem());
    }

    /** The type of pipe in the off hand, or null. */
    @Nullable
    public static PipeType offhandType(Minecraft mc) {
        if (mc.player == null) return null;
        return mc.player.getOffhandItem().getItem() instanceof BlockItem item && item.getBlock() instanceof PipeBlock pipe
                ? pipe.type() : null;
    }

    /** Whether a keyboard key binding is held right now (works when another binding shares the key, like sprint). */
    static boolean held(Minecraft mc, KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        if (bound.getType() != InputConstants.Type.KEYSYM) return key.isDown();
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), bound.getValue());
    }

    /** The middle button (MinecraftMixin, before vanilla picks a block): true when it turned the grid. */
    public static boolean pickBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !active(mc)) return false;
        CurveTarget target = CurveHighlight.current();
        CurvePlacer.Action action = CurvePlacer.decide(true, CurvePlacer.drawing(),
                target == null ? CurveTarget.Kind.AIR : target.kind, mc.player.isShiftKeyDown(), CurvePlacer.Click.PICK,
                CurvePlacer.held(mc.player.getMainHandItem()));
        if (action != CurvePlacer.Action.GRID_NEXT && action != CurvePlacer.Action.GRID_PREVIOUS) return false;
        CurvePlacer.cycleGrid(action == CurvePlacer.Action.GRID_NEXT);
        mc.player.displayClientMessage(Component.translatable("hud.flowline.grid.changed",
                Component.translatable(CurvePlacer.grid().key())), true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        // the middle button is handled before vanilla's pick (pickBlock above)
        if (mc.player == null || mc.level == null || mc.player.isSpectator() || event.isPickBlock()) return;
        boolean curved = active(mc);
        if (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND) {
            // the off-hand pipe never places a block while curved mode draws
            if (curved) event.setCanceled(true);
            return;
        }
        CurveTarget target = CurveHighlight.current();
        if (target == null) return;
        CurvePlacer.Click click = event.isAttack() ? CurvePlacer.Click.ATTACK : CurvePlacer.Click.USE;
        if (CurvePlacer.moving() >= 0 && curved) {
            // moving a node: right-click puts it at the target, left-click gives up
            event.setCanceled(true);
            event.setSwingHand(false);
            if (click == CurvePlacer.Click.USE && target.kind != CurveTarget.Kind.EDGE && target.kind != CurveTarget.Kind.NODE) {
                var to = CurvePlacer.end(target);
                ModNetwork.sendToServer(new CurveTranslatePayload(CurvePlacer.nextRequest(), CurvePlacer.moving(), to.pos(), to.face()));
            }
            CurvePlacer.stopMoving();
            return;
        }
        boolean shift = mc.player.isShiftKeyDown();
        CurvePlacer.Action action = CurvePlacer.decide(curved, CurvePlacer.drawing(), target.kind, shift, click,
                CurvePlacer.held(mc.player.getMainHandItem()));
        if (action == CurvePlacer.Action.PASS) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        switch (action) {
            case START -> {
                PipeType type = offhandType(mc);
                if (type != null) CurvePlacer.begin(target, type);
            }
            case ADD -> CurvePlacer.add(target);
            case FINISH -> CurvePlacer.finish(target);
            case BREAK -> {
                if (!mc.player.getAbilities().instabuild) {
                    // survival: held like mining a block (CurveBreaking); the click itself only starts it
                    event.setSwingHand(true);
                    CurveBreaking.start(target);
                    return;
                }
                long now = mc.level.getGameTime();
                if (!CurvePlacer.breakReady(now, lastBreak)) return;
                lastBreak = now;
                event.setSwingHand(true);
                ModNetwork.sendToServer(target.kind == CurveTarget.Kind.NODE
                        ? new CurveRemovePayload(CurvePlacer.nextRequest(), target.nodeId)
                        : new CurveBreakPayload(CurvePlacer.nextRequest(), target.edgeId));
            }
            case PAINT -> {
                event.setSwingHand(true);
                ModNetwork.sendToServer(new CurvePaintPayload(CurvePlacer.nextRequest(), target.edgeId, InteractionHand.MAIN_HAND));
            }
            case OPEN_END -> end(target, CurveEndPayload.Action.OPEN);
            case CYCLE_END -> end(target, CurveEndPayload.Action.CYCLE);
            case CARD_END -> end(target, CurveEndPayload.Action.CARD);
            default -> {}
        }
    }

    private static void end(CurveTarget target, CurveEndPayload.Action action) {
        ModNetwork.sendToServer(new CurveEndPayload(CurvePlacer.nextRequest(), target.nodeId, action));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        CurvePlacer.setNoSnap(mc.screen == null && held(mc, Keys.NO_SNAP));
        if (CurvePlacer.drawing() && (!active(mc) || offhandType(mc) != CurvePlacer.drawingType())) CurvePlacer.cancel();
        if (CurvePlacer.moving() >= 0 && !active(mc)) CurvePlacer.stopMoving();
    }

    /** Escape while drawing drops the half-drawn chain instead of opening the pause menu. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening event) {
        if ((CurvePlacer.drawing() || CurvePlacer.moving() >= 0) && event.getNewScreen() instanceof PauseScreen) {
            CurvePlacer.cancel();
            CurvePlacer.stopMoving();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CurvePlacer.cancel();
        CurvePlacer.stopMoving();
        ClientCurves.INSTANCE.reset();
    }
}
