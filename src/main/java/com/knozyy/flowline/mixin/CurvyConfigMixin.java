package com.knozyy.flowline.mixin;

import com.knozyy.flowline.compat.CurvyPipesCompat;
import com.knozyy.flowline.pipe.OffhandMode;
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

    /** Curvy's energy lookup finds a {@link com.knozyy.flowline.compat.CurvyPort} on Flowline pipes. */
    @SuppressWarnings("rawtypes")
    @org.spongepowered.asm.mixin.injection.Redirect(method = "resolveEnergyCap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getCapability(Lnet/minecraftforge/common/"
                    + "capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static net.minecraftforge.common.util.LazyOptional flowline$energyPort(
            net.minecraft.world.level.block.entity.BlockEntity be, net.minecraftforge.common.capabilities.Capability cap,
            net.minecraft.core.Direction side) {
        return com.knozyy.flowline.compat.CurvyPort.capability(be, cap, side);
    }

    @Inject(method = {"onIdMap", "onCommonSetup"}, at = @At("TAIL"))
    private static void flowline$restoreBlockItems(CallbackInfo ci) {
        CurvyPipesCompat.bindBlocks();
    }

    /** Curvy only handles a Flowline pipe in the off hand of a player in Curvy mode; main-hand pipes place blocks. */
    private static boolean flowline$notCurvy(PlayerInteractEvent event) {
        return CurvyPipesCompat.supported(event.getItemStack()) && (event.getHand() == InteractionHand.MAIN_HAND
                || OffhandMode.of(event.getEntity()) != OffhandMode.CURVY);
    }

    /** A universal pipe in Curvy mode is looked up as the native pipe of its channel; see UniversalChannel. */
    @Inject(method = "itemId", at = @At("RETURN"), cancellable = true)
    private static void flowline$universalChannel(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(com.knozyy.flowline.compat.UniversalChannel.rewrite(stack, cir.getReturnValue()));
    }

    @Inject(method = "onRightClickBlock", at = @At(value = "INVOKE",
            target = "Lcyb0124/curvy_pipes/common/CommonHandler;itemId(Lnet/minecraft/world/item/ItemStack;)I"))
    private static void flowline$blockChannel(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        com.knozyy.flowline.compat.UniversalChannel.pending(event.getEntity(), event.getHand());
    }

    @Inject(method = "onRightClickItem", at = @At(value = "INVOKE",
            target = "Lcyb0124/curvy_pipes/common/CommonHandler;itemId(Lnet/minecraft/world/item/ItemStack;)I"))
    private static void flowline$itemChannel(PlayerInteractEvent.RightClickItem event, CallbackInfo ci) {
        com.knozyy.flowline.compat.UniversalChannel.pending(event.getEntity(), event.getHand());
    }

    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true)
    private static void flowline$normalBlockPlacement(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (flowline$notCurvy(event)) ci.cancel();
    }

    @Inject(method = "onRightClickItem", at = @At("HEAD"), cancellable = true)
    private static void flowline$normalItemUse(PlayerInteractEvent.RightClickItem event, CallbackInfo ci) {
        if (flowline$notCurvy(event)) ci.cancel();
    }
}
