package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.EndCondition;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.Anchor;
import com.knozyy.flowline.curved.CurveData;
import com.knozyy.flowline.curved.CurveSync;
import com.knozyy.flowline.curved.PlaceRequest;
import com.knozyy.flowline.network.curve.CurveBreakPayload;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.network.curve.CurveEndPayload;
import com.knozyy.flowline.network.curve.CurveForceJointPayload;
import com.knozyy.flowline.network.curve.CurveForgetPayload;
import com.knozyy.flowline.network.curve.CurveInsertPayload;
import com.knozyy.flowline.network.curve.CurvePlacePayload;
import com.knozyy.flowline.network.curve.CurveRemovePayload;
import com.knozyy.flowline.network.curve.CurveResultPayload;
import com.knozyy.flowline.network.curve.CurveTranslatePayload;
import com.knozyy.flowline.pipe.PipeType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Curved pipe packets and how the server keeps clients' copies in step. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class CurveSyncGameTests {
    private static <T> T trip(T payload, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encode.accept(payload, buf);
        T back = decode.apply(buf);
        if (buf.readableBytes() != 0) throw new IllegalStateException(payload + " left " + buf.readableBytes() + " bytes");
        return back;
    }

    private static CurveEdgesPayload.EdgeSync edgeSync(long id, long from, long to) {
        return new CurveEdgesPayload.EdgeSync(id, from, to, PipeType.FLUID, 4, 0.37,
                List.of(new Vec3d(0.1, 0.2, Math.PI), new Vec3d(1, 2, 3)));
    }

    private static CurveEdgesPayload.NodeSync nodeSync(long id, Vec3d pos) {
        return new CurveEdgesPayload.NodeSync(id, pos, false, (byte) 2, true);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curvePayloadsRoundTrip(GameTestHelper h) {
        PlaceRequest request = new PlaceRequest(PipeType.ITEM,
                new PlaceRequest.End(new Vec3(1.25, 2, 3), OptionalLong.empty(),
                        Optional.of(new PlaceRequest.Face(new BlockPos(1, 2, 3), Direction.EAST))),
                new PlaceRequest.End(new Vec3(-5, 64.5, 1e-3), OptionalLong.of(7), Optional.empty()),
                List.of(new Vec3(0.5, 0.25, 0.125)));
        List<Object> payloads = List.of(
                new CurvePlacePayload(3, request), new CurveBreakPayload(4, 99), new CurveInsertPayload(5, 12, 3, 0.75),
                new CurveRemovePayload(6, 8), new CurveTranslatePayload(7, 8, new Vec3(1, 2, 3),
                        Optional.of(new PlaceRequest.Face(BlockPos.ZERO, Direction.DOWN))),
                new CurveTranslatePayload(8, 9, new Vec3(4, 5, 6), Optional.empty()),
                new CurveForceJointPayload(9, 10, true), new CurveEndPayload(10, 11, CurveEndPayload.Action.CYCLE),
                new CurveResultPayload(11, 3, false, 6), new CurveForgetPayload(new ChunkPos(-3, 7)),
                new CurveEdgesPayload(new ChunkPos(2, -1), List.of(edgeSync(1, 2, 3)),
                        List.of(nodeSync(2, new Vec3d(0, 0, 0)), nodeSync(3, new Vec3d(1, 2, 3))), List.of(5L), List.of(6L, 7L)));
        for (Object payload : payloads) {
            Object back;
            if (payload instanceof CurvePlacePayload p) back = trip(p, CurvePlacePayload::encode, CurvePlacePayload::decode);
            else if (payload instanceof CurveBreakPayload p) back = trip(p, CurveBreakPayload::encode, CurveBreakPayload::decode);
            else if (payload instanceof CurveInsertPayload p) back = trip(p, CurveInsertPayload::encode, CurveInsertPayload::decode);
            else if (payload instanceof CurveRemovePayload p) back = trip(p, CurveRemovePayload::encode, CurveRemovePayload::decode);
            else if (payload instanceof CurveTranslatePayload p) back = trip(p, CurveTranslatePayload::encode, CurveTranslatePayload::decode);
            else if (payload instanceof CurveForceJointPayload p) back = trip(p, CurveForceJointPayload::encode, CurveForceJointPayload::decode);
            else if (payload instanceof CurveEndPayload p) back = trip(p, CurveEndPayload::encode, CurveEndPayload::decode);
            else if (payload instanceof CurveResultPayload p) back = trip(p, CurveResultPayload::encode, CurveResultPayload::decode);
            else if (payload instanceof CurveForgetPayload p) back = trip(p, CurveForgetPayload::encode, CurveForgetPayload::decode);
            else if (payload instanceof CurveEdgesPayload p) back = trip(p, CurveEdgesPayload::encode, CurveEdgesPayload::decode);
            else throw new IllegalStateException(payload.toString());
            h.assertTrue(back.equals(payload), "round trip of " + payload + " gave " + back);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void edgeChunksCoverItsBoxes(GameTestHelper h) {
        CurveEdge edge = new CurveEdge(1, 1, 2, 0.375, CurveGraphGameTests.straight(new Vec3d(0, 0.5, 0.5), new Vec3d(40, 0.5, 0.5)));
        h.assertTrue(CurveSync.chunksOf(edge).equals(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0))),
                "a 40 block edge along x from 0 reaches chunks 0, 1 and 2, got " + CurveSync.chunksOf(edge));
        h.succeed();
    }

    @GameTest(template = CurveNetworkGameTests.TEMPLATE, timeoutTicks = 20)
    public static void syncQueuesChangedEdges(GameTestHelper h) {
        long a = CurveTestSupport.node(h, new Vec3(1.5, 2.5, 4.5), Anchor.FREE, PipeType.ITEM);
        long b = CurveTestSupport.node(h, new Vec3(6.5, 2.5, 4.5), Anchor.FREE, PipeType.ITEM);
        long edge = CurveTestSupport.edge(h, a, b, PipeType.ITEM, EndCondition.FREE, EndCondition.FREE);
        Set<ChunkPos> chunks = CurveSync.chunksOf(CurveData.get(h.getLevel()).graph().edge(edge).orElseThrow());
        h.assertTrue(CurveSync.pending(h.getLevel()).containsAll(chunks), "the edge's chunks wait to be sent");
        // other tests change curves in the same chunks at any time, so the tick's own flush is not waited for here
        CurveSync.flush(h.getLevel());
        h.assertTrue(CurveSync.pending(h.getLevel()).stream().noneMatch(chunks::contains), "a flush sends them");
        CurveTestSupport.clear(h);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void clientStoreForgetsAfterLastChunk(GameTestHelper h) {
        ClientCurves store = new ClientCurves();
        ChunkPos one = new ChunkPos(0, 0), two = new ChunkPos(1, 0);
        List<CurveEdgesPayload.NodeSync> nodes = List.of(nodeSync(2, new Vec3d(0.1, 0.2, Math.PI)), nodeSync(3, new Vec3d(1, 2, 3)));
        store.apply(new CurveEdgesPayload(one, List.of(edgeSync(1, 2, 3)), nodes, List.of(), List.of()));
        store.apply(new CurveEdgesPayload(two, List.of(edgeSync(1, 2, 3)), nodes, List.of(), List.of()));
        h.assertTrue(store.graph().edge(1).isPresent(), "the edge arrives");
        store.forget(one);
        h.assertTrue(store.graph().edge(1).isPresent() && store.node(2) != null, "still seen through the second chunk");
        store.forget(two);
        h.assertTrue(store.graph().edge(1).isEmpty() && store.node(2) == null, "gone with the last chunk");
        h.succeed();
    }
}
