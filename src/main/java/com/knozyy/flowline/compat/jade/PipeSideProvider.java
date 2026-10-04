package com.knozyy.flowline.compat.jade;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.PipeSideInfo;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Server: packs the looked-at side's settings. Client: shows them under the pipe's name. */
public enum PipeSideProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(Flowline.MODID, "pipe_side");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PipeBlockEntity pipe)) return;
        Direction side = PipeBlock.sideFromHit(accessor.getHitResult(), accessor.getPosition(), pipe.facade() != null);
        data.put("flowline", PipeSideInfo.collect(pipe, side));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        PipeSideInfo.lines(accessor.getServerData().getCompound("flowline")).forEach(tooltip::add);
    }
}
