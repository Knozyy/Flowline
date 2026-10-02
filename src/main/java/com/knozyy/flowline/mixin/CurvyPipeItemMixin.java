package com.knozyy.flowline.mixin;

import com.knozyy.flowline.compat.CurvyPipesCompat;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.compat.PipePlacement;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** A native Curvy item can also place its ordinary Flowline block. */
@Pseudo
@Mixin(targets = "cyb0124.curvy_pipes.common.BuiltInPipeItem", remap = false)
public abstract class CurvyPipeItemMixin extends Item {
    protected CurvyPipeItemMixin(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        PipeBlock block = CurvyPipesCompat.block(this);
        return block == null ? super.useOn(context) : PipePlacement.useOn(block, context);
    }

    @Override
    public String getDescriptionId() {
        PipeBlock block = CurvyPipesCompat.block(this);
        return block == null ? super.getDescriptionId() : block.getDescriptionId();
    }
}
