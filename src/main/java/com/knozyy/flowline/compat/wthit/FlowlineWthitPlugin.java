package com.knozyy.flowline.compat.wthit;

import com.knozyy.flowline.compat.PipeSideInfo;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** WTHIT: shows the looked-at side's mode and settings. Declared in {@code waila_plugins.json}. */
public class FlowlineWthitPlugin implements IWailaPlugin, IBlockComponentProvider, IDataProvider<PipeBlockEntity> {
    @Override
    public void register(IRegistrar registrar) {
        registrar.addBlockData(this, PipeBlockEntity.class);
        registrar.addComponent(this, TooltipPosition.BODY, PipeBlock.class);
    }

    @Override
    public void appendData(IDataWriter writer, IServerAccessor<PipeBlockEntity> accessor, IPluginConfig config) {
        PipeBlockEntity pipe = accessor.getTarget();
        HitResult hit = accessor.getHitResult();
        if (!(hit instanceof BlockHitResult block)) return;
        Direction side = PipeBlock.sideFromHit(block, pipe.getBlockPos());
        writer.raw().put("flowline", PipeSideInfo.collect(pipe, side));
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getData().raw().getCompound("flowline");
        PipeSideInfo.lines(tag).forEach(tooltip::addLine);
    }
}
