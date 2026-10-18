package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.curved.CurveData;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * The GameTest world is reused between runs, so curves left by a test that failed before cleaning up would sit where
 * the same test builds again. Only on the GameTest server, every level starts without curved pipes.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class CurveTestReset {
    private CurveTestReset() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CurveData data = CurveData.get(level);
            for (CurveEdge edge : new ArrayList<>(data.graph().edges())) data.removeEdge(edge.id());
            for (CurveNode node : new ArrayList<>(data.graph().nodes())) data.removeNode(node.id());
        }
    }
}
