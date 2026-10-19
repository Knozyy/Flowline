package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change in a loaded chunk, also the ones that tell no neighbour (pistons, some commands), is noted for
 * curved pipes, like Curvy Pipes' hook on the same method (curvy-behaviour-addendum-re.md, B4).
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void flowline$noteCurveChange(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) CurveData.blockChanged(((LevelChunk) (Object) this).getLevel(), pos);
    }
}
