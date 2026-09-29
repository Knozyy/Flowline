package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.WrenchScrollPayload;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Sneak + scroll with the wrench on a pipe side: quick settings instead of switching hotbar slots. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.player.isShiftKeyDown()) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof PipeBlock)) return;
        if (event.getScrollDeltaY() == 0) return;
        boolean facade = mc.level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.facade() != null;
        Direction side = PipeBlock.sideFromHit(hit, pos, facade);
        PacketDistributor.sendToServer(new WrenchScrollPayload(pos, side, event.getScrollDeltaY() > 0,
                Screen.hasControlDown()));
        event.setCanceled(true);
    }

}
