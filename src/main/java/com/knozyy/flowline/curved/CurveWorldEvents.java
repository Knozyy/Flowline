package com.knozyy.flowline.curved;

import com.knozyy.flowline.Flowline;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Notes every block change that tells its neighbours (placing, breaking, explosions…) for {@link CurveData#processDirty}.
 * Changes that tell nobody, such as some piston moves, are caught by a hook on {@code LevelChunk.setBlockState} later.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class CurveWorldEvents {
    private CurveWorldEvents() {}

    @SubscribeEvent
    public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) CurveData.get(level).markDirty(event.getPos());
    }
}
