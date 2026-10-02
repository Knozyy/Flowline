package com.knozyy.flowline.network;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.pipe.NetworkView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "6";
    private static final Map<ServerPlayer, Integer> NETWORK_QUERIES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> BUILD_REQUESTS = new WeakHashMap<>();
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
        CHANNEL.messageBuilder(TravelPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TravelPayload::encode).decoder(TravelPayload::decode)
                .consumerMainThread(ModNetwork::onTravel).add();
        CHANNEL.messageBuilder(BuildPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildPayload::encode).decoder(BuildPayload::decode)
                .consumerMainThread(ModNetwork::onBuild).add();
        CHANNEL.messageBuilder(NetworkQueryPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NetworkQueryPayload::encode).decoder(NetworkQueryPayload::decode)
                .consumerMainThread(ModNetwork::onNetworkQuery).add();
        CHANNEL.messageBuilder(NetworkViewPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NetworkViewPayload::encode).decoder(NetworkViewPayload::decode)
                .consumerMainThread(ModNetwork::onNetworkView).add();
        CHANNEL.messageBuilder(FluidFlowPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FluidFlowPayload::encode).decoder(FluidFlowPayload::decode)
                .consumerMainThread(ModNetwork::onFluidFlow).add();

    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static void onSetEntry(SetFilterEntryPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.setEntry(payload.index(), payload.entry().orElse(null));
        }
        context.get().setPacketHandled(true);
    }

    private static void onSetValue(SetSideValuePayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.setValue(payload.field(), payload.value());
        }
        context.get().setPacketHandled(true);
    }

    private static void onWrenchScroll(WrenchScrollPayload payload, Supplier<NetworkEvent.Context> context) {
        Player player = context.get().getSender();
        if (player != null && WrenchItem.isWrench(player.getMainHandItem())
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) <= 64) {
            WrenchItem.scroll(player, payload.pos(), payload.side(), payload.forward(), payload.redstone());
        }
        context.get().setPacketHandled(true);
    }

    private static void onBuild(BuildPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().setPacketHandled(true);
        if (player == null) return;
        Integer last = BUILD_REQUESTS.get(player);
        if (last != null && player.tickCount - last < 20) return;
        BUILD_REQUESTS.put(player, player.tickCount);
        com.knozyy.flowline.pipe.PipeBuilder.build(player);
    }

    private static void onNetworkQuery(NetworkQueryPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().setPacketHandled(true);
        if (player == null) return;
        Integer last = NETWORK_QUERIES.get(player);
        if (last != null && player.tickCount - last < 20) return;
        NETWORK_QUERIES.put(player, player.tickCount);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NetworkViewPayload(player.level().dimension().location(), payload.pos(), NetworkView.query(player, payload.pos())));
    }

    private static void onNetworkView(NetworkViewPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.networkView(payload));
        context.get().setPacketHandled(true);
    }

    private static void onFluidFlow(FluidFlowPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.fluidFlow(payload));
        context.get().setPacketHandled(true);
    }

    private static void onFilterPage(FilterPagePayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.filterPage(payload));
        context.get().setPacketHandled(true);
    }

    private static void onTravel(TravelPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.travel(payload));
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

        static void travel(TravelPayload payload) {
            com.knozyy.flowline.client.TravellingItems.add(payload);
        }

        static void networkView(NetworkViewPayload payload) {
            com.knozyy.flowline.client.NetworkOverlay.receive(payload);
        }

        static void fluidFlow(FluidFlowPayload payload) {
            com.knozyy.flowline.client.FlowingFluids.add(payload);
        }
    }
}
