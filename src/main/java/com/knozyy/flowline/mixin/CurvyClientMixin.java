package com.knozyy.flowline.mixin;

import com.knozyy.flowline.client.OffhandPipe;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import com.knozyy.flowline.compat.UniversalChannel;
import com.knozyy.flowline.pipe.OffhandMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Curvy's client only sees a Flowline pipe in the off hand, and only in Curvy mode: a pipe in the main hand always
 * places a block, and the off hand's other mode is Build for me.
 */
@Pseudo
@Mixin(targets = "cyb0124.curvy_pipes.client.ClientHandler", remap = false)
public abstract class CurvyClientMixin {
    private static boolean flowline$curvyOffhand() {
        var player = Minecraft.getInstance().player;
        return player != null && CurvyPipesCompat.supported(player.getOffhandItem())
                && OffhandPipe.mode() == OffhandMode.CURVY;
    }

    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true)
    private static void flowline$handRules(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean main = CurvyPipesCompat.supported(player.getMainHandItem());
        boolean off = CurvyPipesCompat.supported(player.getOffhandItem());
        if (event.isUseItem()) {
            boolean pipe = CurvyPipesCompat.supported(player.getItemInHand(event.getHand()));
            if (pipe && (event.getHand() == InteractionHand.MAIN_HAND || !flowline$curvyOffhand())) ci.cancel();
        }
        if (event.isAttack() && (main || off && !flowline$curvyOffhand())) ci.cancel();
    }

    /** Native `interact` asks for the item id of the held stack; a universal pipe answers with its channel's native one. */
    @Inject(method = "onInteract", at = @At(value = "INVOKE",
            target = "Lcyb0124/curvy_pipes/client/ClientHandler;interact(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/item/ItemStack;Z)Z"))
    private static void flowline$channelBefore(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null) UniversalChannel.pending(player, event.getHand());
    }

    @Inject(method = "onInteract", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcyb0124/curvy_pipes/client/ClientHandler;interact(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/item/ItemStack;Z)Z"))
    private static void flowline$channelAfter(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        UniversalChannel.clear();
    }

    @ModifyArg(method = "renderLevel(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V",
            at = @At(value = "INVOKE", target = "Lcyb0124/curvy_pipes/client/ClientHandler;renderLevel(DDDZZIIIIDFII)V"), index = 11)
    private static int flowline$noMainHandCurvePreview(int itemId) {
        var player = Minecraft.getInstance().player;
        return player != null && CurvyPipesCompat.supported(player.getMainHandItem()) ? 0 : itemId;
    }

    @ModifyArg(method = "renderLevel(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V",
            at = @At(value = "INVOKE", target = "Lcyb0124/curvy_pipes/client/ClientHandler;renderLevel(DDDZZIIIIDFII)V"), index = 12)
    private static int flowline$offhandCurvePreviewInCurvyMode(int itemId) {
        var player = Minecraft.getInstance().player;
        if (player == null || !CurvyPipesCompat.supported(player.getOffhandItem())) return itemId;
        if (!flowline$curvyOffhand()) return 0;
        return CurvyPipesCompat.nativeId(player.getOffhandItem(), OffhandPipe.channel());
    }
}
