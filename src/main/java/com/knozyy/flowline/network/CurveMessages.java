package com.knozyy.flowline.network;

import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curved.CurveEdits;
import com.knozyy.flowline.curved.EditResult;
import com.knozyy.flowline.network.curve.CurveBreakPayload;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.network.curve.CurveForceJointPayload;
import com.knozyy.flowline.network.curve.CurveForgetPayload;
import com.knozyy.flowline.network.curve.CurveInsertPayload;
import com.knozyy.flowline.network.curve.CurvePaintPayload;
import com.knozyy.flowline.network.curve.CurvePlacePayload;
import com.knozyy.flowline.network.curve.CurveRemovePayload;
import com.knozyy.flowline.network.curve.CurveResultPayload;
import com.knozyy.flowline.network.curve.CurveTranslatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Function;
import java.util.function.Supplier;

/** The curved pipe packets: edit requests and their results, and the chunk-by-chunk copies clients keep. */
final class CurveMessages {
    private CurveMessages() {}

    static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(CurvePlacePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurvePlacePayload::encode).decoder(CurvePlacePayload::decode)
                .consumerMainThread(server(p -> p.requestId(), (player, p) -> CurveEdits.place(player, p.request()))).add();
        channel.messageBuilder(CurveBreakPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurveBreakPayload::encode).decoder(CurveBreakPayload::decode)
                .consumerMainThread(server(p -> p.requestId(), (player, p) -> CurveEdits.breakEdge(player, p.edge()))).add();
        channel.messageBuilder(CurveInsertPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurveInsertPayload::encode).decoder(CurveInsertPayload::decode)
                .consumerMainThread(server(p -> p.requestId(),
                        (player, p) -> CurveEdits.insert(player, p.edge(), p.segment(), p.along()))).add();
        channel.messageBuilder(CurveRemovePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurveRemovePayload::encode).decoder(CurveRemovePayload::decode)
                .consumerMainThread(server(p -> p.requestId(), (player, p) -> CurveEdits.removeNode(player, p.node()))).add();
        channel.messageBuilder(CurveTranslatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurveTranslatePayload::encode).decoder(CurveTranslatePayload::decode)
                .consumerMainThread(server(p -> p.requestId(),
                        (player, p) -> CurveEdits.translate(player, p.node(), p.pos(), p.face()))).add();
        channel.messageBuilder(CurveForceJointPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurveForceJointPayload::encode).decoder(CurveForceJointPayload::decode)
                .consumerMainThread(server(p -> p.requestId(),
                        (player, p) -> CurveEdits.forceJoint(player, p.node(), p.desired()))).add();
        channel.messageBuilder(com.knozyy.flowline.network.curve.CurveEndPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.knozyy.flowline.network.curve.CurveEndPayload::encode)
                .decoder(com.knozyy.flowline.network.curve.CurveEndPayload::decode)
                .consumerMainThread(server(p -> p.requestId(), (player, p) -> switch (p.action()) {
                    case OPEN -> CurveEdits.openEnd(player, p.node());
                    case CYCLE -> CurveEdits.cycleEnd(player, p.node());
                    case CARD -> CurveEdits.useCardOnEnd(player, p.node(), net.minecraft.world.InteractionHand.MAIN_HAND);
                })).add();
        channel.messageBuilder(CurvePaintPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CurvePaintPayload::encode).decoder(CurvePaintPayload::decode)
                .consumerMainThread(server(p -> p.requestId(), (player, p) -> CurveEdits.paint(player, p.edge(), p.hand()))).add();
        channel.messageBuilder(CurveResultPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CurveResultPayload::encode).decoder(CurveResultPayload::decode)
                .consumerMainThread((CurveResultPayload p, Supplier<NetworkEvent.Context> c) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCurves.INSTANCE.lastResult = p);
                    c.get().setPacketHandled(true);
                }).add();
        channel.messageBuilder(CurveEdgesPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CurveEdgesPayload::encode).decoder(CurveEdgesPayload::decode)
                .consumerMainThread((CurveEdgesPayload p, Supplier<NetworkEvent.Context> c) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCurves.INSTANCE.apply(p));
                    c.get().setPacketHandled(true);
                }).add();
        channel.messageBuilder(CurveForgetPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CurveForgetPayload::encode).decoder(CurveForgetPayload::decode)
                .consumerMainThread((CurveForgetPayload p, Supplier<NetworkEvent.Context> c) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCurves.INSTANCE.forget(p.chunk()));
                    c.get().setPacketHandled(true);
                }).add();
        return id;
    }

    interface Edit<T> {
        EditResult run(ServerPlayer player, T payload);
    }

    /** A server handler that runs an edit and answers with its result. */
    static <T> java.util.function.BiConsumer<T, Supplier<NetworkEvent.Context>> server(Function<T, Integer> requestId, Edit<T> edit) {
        return (payload, context) -> {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;
            EditResult result = edit.run(player, payload);
            ModNetwork.sendToPlayer(player, new CurveResultPayload(requestId.apply(payload),
                    result.error() == null ? 0 : result.error().code(), result.denied(), result.missing()));
        };
    }
}
