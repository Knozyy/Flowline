package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.BuildPayload;
import com.knozyy.flowline.network.WrenchScrollPayload;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.knozyy.flowline.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Sneak + scroll with the wrench on a pipe side: quick settings instead of switching hotbar slots. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.player.isShiftKeyDown()) return;
        if (!WrenchItem.isWrench(mc.player.getMainHandItem())) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof PipeBlock)) return;
        if (event.getScrollDelta() == 0) return;
        boolean facade = mc.level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.facade() != null;
        Direction side = PipeBlock.sideFromHit(hit, pos, facade);
        ModNetwork.sendToServer(new WrenchScrollPayload(pos, side, event.getScrollDelta() > 0,
                Screen.hasControlDown()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || event.getPoseStack() == null) return;
        TravellingItems.render(event.getPoseStack(), event.getCamera().getPosition(),
                event.getPartialTick());
        NetworkOverlay.render(event.getPoseStack(), event.getCamera().getPosition());
        CurveRenderer.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        NetworkOverlay.legend(event.getGuiGraphics());
        CurveClient.hud(event.getGuiGraphics());
        BuildHint.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) TravellingItems.tick(mc.level);
        NetworkOverlay.tick();
        FlowingFluids.tick();
        CurveClient.tick();
        while (Keys.BUILD.consumeClick()) {
            // the server checks the off hand and looks where the player looks itself
            if (mc.player != null && mc.screen == null) ModNetwork.sendToServer(new BuildPayload());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TravellingItems.clear();
        NetworkOverlay.clear();
        FlowingFluids.clear();
        CurveClient.clear();
    }
}
