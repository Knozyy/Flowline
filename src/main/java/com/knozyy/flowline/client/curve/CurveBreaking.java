package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curved.CurveCost;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.curve.CurveBreakPayload;
import com.knozyy.flowline.network.curve.CurveEdgesPayload;
import com.knozyy.flowline.network.curve.CurveRemovePayload;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Breaking a curved pipe in survival like a block: hold the attack button on it, it breaks once the pipe block's own
 * mining time has passed (hit sounds and swings on the way). Looking away or letting go starts over.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class CurveBreaking {
    /** Ticks between a break and the next start, like vanilla's destroy delay. */
    private static final int DELAY = 5;
    @Nullable
    private static CurveTarget.Kind kind;
    private static long id = -1;
    private static float progress;
    private static int ticks, delay;

    private CurveBreaking() {}

    /** How far the current break has got, 0 to 1; 0 when nothing is being broken. */
    public static float progress() {
        return id < 0 ? 0 : progress;
    }

    private static long idOf(CurveTarget target) {
        return target.kind == CurveTarget.Kind.NODE ? target.nodeId : target.edgeId;
    }

    private static boolean same(@Nullable CurveTarget target) {
        return target != null && target.kind == kind && idOf(target) == id;
    }

    /** The attack button went down (or is held) on {@code target}: start breaking it unless that is under way. */
    public static void start(CurveTarget target) {
        if (same(target) || delay > 0) return;
        kind = target.kind;
        id = idOf(target);
        progress = 0;
        ticks = 0;
    }

    private static void reset() {
        kind = null;
        id = -1;
        progress = 0;
    }

    @Nullable
    private static PipeType type(CurveTarget target) {
        ClientCurves curves = ClientCurves.INSTANCE;
        long edge = target.edgeId;
        if (target.kind == CurveTarget.Kind.NODE) {
            List<CurveEdge> at = curves.graph().edgesAt(target.nodeId);
            if (at.isEmpty()) return null;
            edge = at.get(0).id();
        }
        CurveEdgesPayload.EdgeSync sync = curves.edge(edge);
        return sync == null ? null : sync.type();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (delay > 0) delay--;
        if (id < 0) return;
        Minecraft mc = Minecraft.getInstance();
        CurveTarget target = CurveHighlight.current();
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.options.keyAttack.isDown() || !same(target)) {
            reset();
            return;
        }
        PipeType type = type(target);
        if (type == null || !(CurveCost.item(type) instanceof BlockItem item)) {
            reset();
            return;
        }
        BlockState state = item.getBlock().defaultBlockState();
        progress += state.getDestroyProgress(mc.player, mc.level, mc.player.blockPosition());
        if (ticks++ % 4 == 0) {
            SoundType sound = state.getSoundType();
            mc.level.playLocalSound(target.pos.x(), target.pos.y(), target.pos.z(), sound.getHitSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 8, sound.getPitch() * 0.5f, false);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (progress >= 1) {
            ModNetwork.sendToServer(target.kind == CurveTarget.Kind.NODE
                    ? new CurveRemovePayload(CurvePlacer.nextRequest(), target.nodeId)
                    : new CurveBreakPayload(CurvePlacer.nextRequest(), target.edgeId));
            reset();
            delay = DELAY;
        }
    }
}
