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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "10";
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
        CHANNEL.messageBuilder(FilterSyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FilterSyncPayload::encode).decoder(FilterSyncPayload::decode)
                .consumerMainThread(ModNetwork::onFilterSync).add();
        CHANNEL.messageBuilder(BuildPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildPayload::encode).decoder(BuildPayload::decode)
                .consumerMainThread(ModNetwork::onBuild).add();
        CHANNEL.messageBuilder(RuleFromCarriedPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RuleFromCarriedPayload::encode).decoder(RuleFromCarriedPayload::decode)
                .consumerMainThread(ModNetwork::onRuleFromCarried).add();
        CHANNEL.messageBuilder(OffhandModePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OffhandModePayload::encode).decoder(OffhandModePayload::decode)
                .consumerMainThread(ModNetwork::onOffhandMode).add();
    }

    private static void onOffhandMode(OffhandModePayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            com.knozyy.flowline.pipe.OffhandMode.set(player, payload.mode());
            com.knozyy.flowline.pipe.OffhandMode.setChannel(player, payload.channel());
        }
        context.get().setPacketHandled(true);
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

    private static void onRuleFromCarried(RuleFromCarriedPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.ruleFromCarried(payload.index());
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

    private static void onFilterSync(FilterSyncPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.filterSync(payload));
        context.get().setPacketHandled(true);
    }

    /** Only loaded on the client. */
    private static final class ClientHandlers {
        static void filterSync(FilterSyncPayload payload) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof PipeMenu menu && menu.containerId == payload.containerId()) {
                menu.receiveFilter(payload);
            }
        }

    }
}
