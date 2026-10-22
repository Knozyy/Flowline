package com.knozyy.flowline.mixin.client;

import com.knozyy.flowline.client.curve.CurveInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The middle button turns the curved pipe grid even when the crosshair is on nothing: vanilla only asks Forge's click
 * event when something is hit.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void flowline$turnCurveGrid(CallbackInfo ci) {
        if (CurveInput.pickBlock()) ci.cancel();
    }
}
