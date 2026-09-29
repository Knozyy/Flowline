package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Flowline.MODID);

    public static final RegistryObject<BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register("pipe",
            () -> BlockEntityType.Builder.of(PipeBlockEntity::new,
                    ModBlocks.pipes().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private ModBlockEntities() {}
}
