package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Moving entities collide with curved pipes: the pipes join the entity collision shapes that {@code collide} gathers
 * once and uses for the move and the step up (curvy-behaviour-addendum-re.md, B4: Curvy hooks the same query).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> flowline$addCurvedPipes(List<VoxelShape> shapes, Vec3 movement) {
        Entity self = (Entity) (Object) this;
        return CurveCollisions.with(self.level(), self.getBoundingBox().expandTowards(movement), shapes);
    }
}
