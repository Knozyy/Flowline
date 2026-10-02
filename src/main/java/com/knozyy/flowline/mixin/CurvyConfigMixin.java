package com.knozyy.flowline.mixin;

import com.knozyy.flowline.compat.CurvyPipesCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the Java config loader, leaving Curvy's native implementation intact. Pinned to 1.15.8. */
@Pseudo
@Mixin(targets = "cyb0124.curvy_pipes.common.CommonHandler", remap = false)
public abstract class CurvyConfigMixin {
    @ModifyArg(method = "loadConfig()V", at = @At(value = "INVOKE",
            target = "Lcyb0124/curvy_pipes/common/CommonHandler;loadConfig(Ljava/lang/String;)V"), index = 0, remap = false)
    private static String flowline$addNativePipes(String yaml) {
        return CurvyPipesCompat.withFlowlinePipes(yaml);
    }

    @ModifyArg(method = "onRegister", at = @At(value = "INVOKE",
            target = "Lcyb0124/curvy_pipes/common/CommonHandler;registerItems(Lnet/minecraftforge/registries/IForgeRegistry;)V"), index = 0)
    private static IForgeRegistry<Item> flowline$reuseMaterials(IForgeRegistry<Item> registry) {
        return CurvyPipesCompat.materialRegistry(registry);
    }

    @Inject(method = "resolveItem", at = @At("HEAD"), cancellable = true)
    private static void flowline$resolveMaterial(String id, CallbackInfoReturnable<Item> cir) {
        Item material = CurvyPipesCompat.resolveMaterial(id);
        if (material != null) cir.setReturnValue(material);
    }

    @Inject(method = {"onIdMap", "onCommonSetup"}, at = @At("TAIL"))
    private static void flowline$restoreBlockItems(CallbackInfo ci) {
        CurvyPipesCompat.bindBlocks();
    }

    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true)
    private static void flowline$normalBlockPlacement(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.getHand() == InteractionHand.OFF_HAND && CurvyPipesCompat.supported(event.getItemStack())) ci.cancel();
    }

    @Inject(method = "onRightClickItem", at = @At("HEAD"), cancellable = true)
    private static void flowline$normalItemUse(PlayerInteractEvent.RightClickItem event, CallbackInfo ci) {
        if (event.getHand() == InteractionHand.OFF_HAND && CurvyPipesCompat.supported(event.getItemStack())) ci.cancel();
    }
}
