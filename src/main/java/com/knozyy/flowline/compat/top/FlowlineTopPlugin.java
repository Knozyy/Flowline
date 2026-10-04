package com.knozyy.flowline.compat.top;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.PipeSideInfo;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Function;

/** The One Probe: shows the looked-at side's mode and settings. Only loaded when TOP asks for it over IMC. */
public class FlowlineTopPlugin implements Function<ITheOneProbe, Void> {
    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(new IProbeInfoProvider() {
            @Override
            public ResourceLocation getID() {
                return new ResourceLocation(Flowline.MODID, "pipe_side");
            }

            @Override
            public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level, BlockState state,
                                     IProbeHitData data) {
                if (!(level.getBlockEntity(data.getPos()) instanceof PipeBlockEntity pipe)) return;
                BlockHitResult hit = new BlockHitResult(data.getHitVec(), data.getSideHit(), data.getPos(), false);
                Direction side = PipeBlock.sideFromHit(hit, data.getPos(), pipe.facade() != null);
                for (Component line : PipeSideInfo.lines(PipeSideInfo.collect(pipe, side))) info.mcText(line);
            }
        });
        return null;
    }
}
