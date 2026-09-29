package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Flowline.MODID);

    public static final DeferredBlock<PipeBlock> ITEM_PIPE = pipe("item_pipe", PipeType.ITEM, MapColor.COLOR_ORANGE);
    public static final DeferredBlock<PipeBlock> FLUID_PIPE = pipe("fluid_pipe", PipeType.FLUID, MapColor.COLOR_BLUE);
    public static final DeferredBlock<PipeBlock> ENERGY_PIPE = pipe("energy_pipe", PipeType.ENERGY, MapColor.COLOR_RED);
    public static final DeferredBlock<PipeBlock> UNIVERSAL_PIPE =
            pipe("universal_pipe", PipeType.UNIVERSAL, MapColor.COLOR_PURPLE);
    /** Only registered when Mekanism is installed. */
    @Nullable
    public static final DeferredBlock<PipeBlock> CHEMICAL_PIPE = ChemicalCompat.available()
            ? pipe("chemical_pipe", PipeType.CHEMICAL, MapColor.COLOR_LIGHT_GREEN) : null;

    private static DeferredBlock<PipeBlock> pipe(String name, PipeType type, MapColor color) {
        return BLOCKS.registerBlock(name, props -> new PipeBlock(props, type),
                BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(1.0F, 3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .dynamicShape()
                        .requiresCorrectToolForDrops());
    }

    /** Every registered pipe block. */
    public static List<DeferredBlock<PipeBlock>> pipes() {
        List<DeferredBlock<PipeBlock>> pipes = new ArrayList<>(List.of(ITEM_PIPE, FLUID_PIPE, ENERGY_PIPE, UNIVERSAL_PIPE));
        if (CHEMICAL_PIPE != null) pipes.add(CHEMICAL_PIPE);
        return pipes;
    }

    private ModBlocks() {}
}
