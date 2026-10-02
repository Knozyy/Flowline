package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curve.CurveCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Iterator;
import java.util.function.BiFunction;

/** Constructor and Guava computeNext names are identical in development and Forge's SRG runtime. */
@Mixin(value=BlockCollisions.class,remap=false)
public abstract class BlockCollisionsMixin<T> {
    @Unique private Iterator<VoxelShape> flowline$curves;
    @Unique private BiFunction<BlockPos.MutableBlockPos,VoxelShape,T> flowline$provider;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void flowline$init(CollisionGetter world,Entity entity,AABB box,boolean suffocating,
                              BiFunction<BlockPos.MutableBlockPos,VoxelShape,T> provider,CallbackInfo ci){
        if(!suffocating){flowline$curves=CurveCollision.shapes(world,box).iterator();flowline$provider=provider;}
    }
    @Inject(method="computeNext",at=@At("HEAD"),cancellable=true,remap=false)
    private void flowline$next(CallbackInfoReturnable<T> cir){
        if(flowline$curves!=null&&flowline$curves.hasNext()){
            VoxelShape shape=flowline$curves.next();var box=shape.bounds();BlockPos pos=BlockPos.containing(box.getCenter());
            cir.setReturnValue(flowline$provider.apply(pos.mutable(),shape.move(-pos.getX(),-pos.getY(),-pos.getZ())));
        }
    }
}
