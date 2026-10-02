package com.knozyy.flowline.mixin;

import com.knozyy.flowline.compat.CurvyPipesCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cyb0124.curvy_pipes.client.ClientHandler", remap = false)
public abstract class CurvyClientMixin {
    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true)
    private static void flowline$normalOffhand(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (event.isUseItem() && CurvyPipesCompat.supported(player.getOffhandItem())
                && (event.getHand() == InteractionHand.OFF_HAND || player.getMainHandItem().isEmpty())) ci.cancel();
        if (event.isAttack() && CurvyPipesCompat.supported(player.getOffhandItem())
                && !CurvyPipesCompat.supported(player.getMainHandItem())) ci.cancel();
    }

    @ModifyArg(method = "renderLevel(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V",
            at = @At(value = "INVOKE", target = "Lcyb0124/curvy_pipes/client/ClientHandler;renderLevel(DDDZZIIIIDFII)V"), index = 12)
    private static int flowline$noOffhandCurvePreview(int itemId) {
        var player = Minecraft.getInstance().player;
        return player != null && CurvyPipesCompat.supported(player.getOffhandItem()) ? 0 : itemId;
    }
}
