package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Flowline.MODID);

    /** Settings stored on a Configuration or Filter Card. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CARD =
            COMPONENTS.register("card", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    /** The block a facade item shows. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> FACADE =
            COMPONENTS.register("facade", () -> DataComponentType.<BlockState>builder()
                    .persistent(BlockState.CODEC)
                    .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY))
                    .build());

    private ModComponents() {}
}
