package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Flowline.MODID);

    public static final RegistryObject<PipeBlock> ITEM_PIPE = pipe("item_pipe", PipeType.ITEM, MapColor.COLOR_YELLOW);
    public static final RegistryObject<PipeBlock> FLUID_PIPE = pipe("fluid_pipe", PipeType.FLUID, MapColor.COLOR_BLUE);
    public static final RegistryObject<PipeBlock> ENERGY_PIPE = pipe("energy_pipe", PipeType.ENERGY, MapColor.COLOR_ORANGE);
    public static final RegistryObject<PipeBlock> UNIVERSAL_PIPE =
            pipe("universal_pipe", PipeType.UNIVERSAL, MapColor.COLOR_PURPLE);
    /** Only registered when Mekanism is installed. */
    @Nullable
    public static final RegistryObject<PipeBlock> CHEMICAL_PIPE = ChemicalCompat.available()
            ? pipe("chemical_pipe", PipeType.CHEMICAL, MapColor.COLOR_LIGHT_GREEN) : null;

    private static RegistryObject<PipeBlock> pipe(String name, PipeType type, MapColor color) {
        return BLOCKS.register(name, () -> new PipeBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(1.0F, 3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .dynamicShape()
                        .requiresCorrectToolForDrops(), type));
    }

    /** Every registered pipe block. */
    public static List<RegistryObject<PipeBlock>> pipes() {
        List<RegistryObject<PipeBlock>> pipes = new ArrayList<>(List.of(ITEM_PIPE, FLUID_PIPE, ENERGY_PIPE, UNIVERSAL_PIPE));
        if (CHEMICAL_PIPE != null) pipes.add(CHEMICAL_PIPE);
        return pipes;
    }

    private ModBlocks() {}
}
