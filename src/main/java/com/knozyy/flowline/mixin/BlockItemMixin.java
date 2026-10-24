package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveCollisions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Curved pipes obstruct block placement like an entity standing there: a block item is not placed where its collision
 * shape would overlap a curve, so the block does not go in and break the curve the next tick.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void flowline$notIntoCurves(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (CurveCollisions.overlaps(level, state.getCollisionShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ()))) {
            cir.setReturnValue(false);
        }
    }
}
