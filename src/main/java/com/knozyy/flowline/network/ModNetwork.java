package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.menu.PipeMenu;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Flowline.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(SetFilterEntryPayload.TYPE, SetFilterEntryPayload.STREAM_CODEC, ModNetwork::onSetEntry);
        registrar.playToServer(SetSideValuePayload.TYPE, SetSideValuePayload.STREAM_CODEC, ModNetwork::onSetValue);
        registrar.playToServer(WrenchScrollPayload.TYPE, WrenchScrollPayload.STREAM_CODEC, ModNetwork::onWrenchScroll);
        registrar.playToClient(FilterPagePayload.TYPE, FilterPagePayload.STREAM_CODEC, ModNetwork::onFilterPage);
        // the handler only touches the client class when it runs, i.e. on the client
        registrar.playToClient(TravelPayload.TYPE, TravelPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.knozyy.flowline.client.TravellingItems.add(payload)));
    }

    private static void onSetEntry(SetFilterEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.setEntry(payload.index(), payload.entry().orElse(null));
            }
        });
    }

    private static void onSetValue(SetSideValuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.setValue(payload.field(), payload.value());
            }
        });
    }

    private static void onWrenchScroll(WrenchScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.getMainHandItem().getItem() instanceof WrenchItem
                    && player.distanceToSqr(payload.pos().getCenter()) <= 64) {
                WrenchItem.scroll(player, payload.pos(), payload.side(), payload.forward(), payload.redstone());
            }
        });
    }

    private static void onFilterPage(FilterPagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.receivePage(payload.page(), payload.entries());
            }
        });
    }
}
