package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.menu.PipeMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Flowline.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetFilterEntryPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetFilterEntryPayload::encode).decoder(SetFilterEntryPayload::decode)
                .consumerMainThread(ModNetwork::onSetEntry).add();
        CHANNEL.messageBuilder(SetSideValuePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSideValuePayload::encode).decoder(SetSideValuePayload::decode)
                .consumerMainThread(ModNetwork::onSetValue).add();
        CHANNEL.messageBuilder(WrenchScrollPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WrenchScrollPayload::encode).decoder(WrenchScrollPayload::decode)
                .consumerMainThread(ModNetwork::onWrenchScroll).add();
        CHANNEL.messageBuilder(FilterPagePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FilterPagePayload::encode).decoder(FilterPagePayload::decode)
                .consumerMainThread(ModNetwork::onFilterPage).add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static void onSetEntry(SetFilterEntryPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
            menu.setEntry(payload.index(), payload.entry().orElse(null));
        }
        context.get().setPacketHandled(true);
    }

    private static void onSetValue(SetSideValuePayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
            menu.setValue(payload.field(), payload.value());
        }
        context.get().setPacketHandled(true);
    }

    private static void onWrenchScroll(WrenchScrollPayload payload, Supplier<NetworkEvent.Context> context) {
        Player player = context.get().getSender();
        if (player != null && player.getMainHandItem().getItem() instanceof WrenchItem
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) <= 64) {
            WrenchItem.scroll(player, payload.pos(), payload.side(), payload.forward(), payload.redstone());
        }
        context.get().setPacketHandled(true);
    }

    private static void onFilterPage(FilterPagePayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.filterPage(payload));
        context.get().setPacketHandled(true);
    }

    /** Only loaded on the client. */
    private static final class ClientHandlers {
        static void filterPage(FilterPagePayload payload) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.receivePage(payload.page(), payload.entries());
            }
        }

    }
}
