package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveCollisions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A falling block that lands where its collision shape would overlap a curved pipe cannot stay there: it drops as an
 * item, as when it lands on a torch, and the curve stays whole. A redirect, because the result lands in a boolean
 * local that has no name or type to find it by outside the development jar.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean flowline$notIntoCurves(BlockState state, LevelReader reader, BlockPos pos) {
        if (!state.canSurvive(reader, pos)) return false;
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        return !CurveCollisions.overlaps(self.level(), state.getCollisionShape(reader, pos).move(pos.getX(), pos.getY(), pos.getZ()));
    }
}
