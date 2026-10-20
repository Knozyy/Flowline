package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A stand-in for a claim mod: refuses block placing and breaking for the players a test names, nobody else. */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class TestProtection {
    private static final Set<UUID> DENIED = ConcurrentHashMap.newKeySet();

    private TestProtection() {}

    static void deny(Entity player) {
        DENIED.add(player.getUUID());
    }

    static void allow(Entity player) {
        DENIED.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null && DENIED.contains(event.getEntity().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (DENIED.contains(event.getPlayer().getUUID())) event.setCanceled(true);
    }
}
