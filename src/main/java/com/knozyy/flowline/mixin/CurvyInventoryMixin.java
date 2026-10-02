package com.knozyy.flowline.mixin;

import com.knozyy.flowline.compat.CurvyPort;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Curvy's item and fluid lookups find a {@link CurvyPort} on Flowline pipes. Pinned to Curvy Pipes 1.15.8. */
@Pseudo
@Mixin(targets = {"cyb0124.curvy_pipes.inv.ItemInv", "cyb0124.curvy_pipes.inv.FluidInv"}, remap = false)
public abstract class CurvyInventoryMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "resolve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)"
            + "Lnet/minecraftforge/common/util/LazyOptional;"))
    private static LazyOptional flowline$pipePort(BlockEntity be, Capability cap, Direction side) {
        return CurvyPort.capability(be, cap, side);
    }
}
