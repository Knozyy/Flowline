package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Flowline.MODID);

    public static final DeferredBlock<PipeBlock> ITEM_PIPE = pipe("item_pipe", PipeType.ITEM, MapColor.COLOR_ORANGE);
    public static final DeferredBlock<PipeBlock> FLUID_PIPE = pipe("fluid_pipe", PipeType.FLUID, MapColor.COLOR_BLUE);
    public static final DeferredBlock<PipeBlock> ENERGY_PIPE = pipe("energy_pipe", PipeType.ENERGY, MapColor.COLOR_RED);

    private static DeferredBlock<PipeBlock> pipe(String name, PipeType type, MapColor color) {
        return BLOCKS.registerBlock(name, props -> new PipeBlock(props, type),
                BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(1.0F, 3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .requiresCorrectToolForDrops());
    }

    private ModBlocks() {}
}
