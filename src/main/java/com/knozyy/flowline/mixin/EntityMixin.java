package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Moving entities collide with curved pipes: the pipes join the entity collision shapes that {@code collide} gathers
 * once and uses for the move and the step up. A pose that would put the entity into a curve is not taken, so a
 * crouching player under a curve stays crouched.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    protected abstract AABB getBoundingBoxForPose(Pose pose);

    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> flowline$addCurvedPipes(List<VoxelShape> shapes, Vec3 movement) {
        Entity self = (Entity) (Object) this;
        return CurveCollisions.with(self.level(), self.getBoundingBox().expandTowards(movement), shapes);
    }

    @Inject(method = "canEnterPose", at = @At("RETURN"), cancellable = true)
    private void flowline$noPoseIntoCurves(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (cir.getReturnValueZ() && CurveCollisions.overlaps(self.level(), getBoundingBoxForPose(pose).deflate(1.0E-7))) {
            cir.setReturnValue(false);
        }
    }
}
