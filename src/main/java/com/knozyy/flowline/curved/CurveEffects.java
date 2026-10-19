package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.CurveEdge;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** What a breaking curved pipe looks and sounds like: the block pipe's break sound and its pieces along the curve. */
public final class CurveEffects {
    /** Every this many points along the edge gets a puff of pieces. */
    private static final int EVERY = 2;
    private static final int PIECES = 4;

    private CurveEffects() {}

    /** Plays the break of {@code edge} (already gone from the data) for everyone nearby. */
    public static void broken(ServerLevel level, CurveEdge edge, PipeType type) {
        if (!(CurveCost.item(type) instanceof BlockItem item)) return;
        BlockState state = item.getBlock().defaultBlockState();
        List<Vec3d> points = edge.shape().points();
        BlockParticleOption pieces = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < points.size(); i += EVERY) {
            Vec3d p = points.get(i);
            level.sendParticles(pieces, p.x(), p.y(), p.z(), PIECES, 0.1, 0.1, 0.1, 0.05);
        }
        // like a broken block: its break sound, a little louder and lower than a step (Level.destroyBlock)
        SoundType sound = state.getSoundType();
        Vec3d middle = points.get(points.size() / 2);
        level.playSound(null, middle.x(), middle.y(), middle.z(), sound.getBreakSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
    }
}
