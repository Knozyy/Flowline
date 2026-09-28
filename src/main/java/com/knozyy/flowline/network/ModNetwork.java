package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.menu.PipeMenu;
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
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetFilterEntryPayload.TYPE, SetFilterEntryPayload.STREAM_CODEC, ModNetwork::onSetEntry);
        registrar.playToClient(FilterPagePayload.TYPE, FilterPagePayload.STREAM_CODEC, ModNetwork::onFilterPage);
    }

    private static void onSetEntry(SetFilterEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.setEntry(payload.index(), payload.entry().orElse(null));
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
