package com.knozyy.flowline.mixin;

import com.knozyy.flowline.curved.CurveCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A sneaking player stops at a curved pipe's edge, not on it: the edge check asks whether the ground below is free,
 * and a curve there is ground. Redirects, because the checks sit inside a loop of the same call.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Redirect(method = {"maybeBackOffFromEdge", "isAboveGround"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean flowline$curvesAreGround(Level level, Entity entity, AABB area) {
        return level.noCollision(entity, area) && !CurveCollisions.overlaps(level, area);
    }
}
