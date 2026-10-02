package com.knozyy.flowline.compat;

import com.knozyy.flowline.Flowline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only notice for legacy worlds. Never marks the archive dirty or runs the removed curve engine. */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class LegacyCurveNotice {
    private LegacyCurveNotice() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { notifyPlayer(event); }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { notifyPlayer(event); }

    private static void notifyPlayer(PlayerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Archive archive = player.serverLevel().getDataStorage().get(Archive::new, "flowline_curves");
        if (archive != null && !archive.tag.getList("nodes", Tag.TAG_COMPOUND).isEmpty())
            player.sendSystemMessage(Component.translatable("message.flowline.curvy.legacy"));
    }

    private static final class Archive extends SavedData {
        private final CompoundTag tag;
        private Archive(CompoundTag tag) { this.tag = tag.copy(); }
        @Override public CompoundTag save(CompoundTag target) { return tag.copy(); }
    }
}
