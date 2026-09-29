package com.knozyy.flowline.client;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A pipe model that shows its facade block instead of the pipe when the block entity has one. */
public class FacadeModel extends BakedModelWrapper<BakedModel> {
    public FacadeModel(BakedModel original) {
        super(original);
    }

    @Nullable
    private static BlockState facade(ModelData data) {
        return data.get(PipeBlockEntity.FACADE);
    }

    private static BakedModel modelOf(BlockState facade) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(facade);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockState facade = facade(data);
        if (facade == null) return super.getQuads(state, side, rand, data, renderType);
        return modelOf(facade).getQuads(facade, side, rand, ModelData.EMPTY, renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState facade = facade(data);
        if (facade == null) return super.getRenderTypes(state, rand, data);
        return modelOf(facade).getRenderTypes(facade, rand, ModelData.EMPTY);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState facade = facade(data);
        if (facade == null) return super.useAmbientOcclusion(state, data, renderType);
        return modelOf(facade).useAmbientOcclusion(facade, ModelData.EMPTY, renderType);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState facade = facade(data);
        return facade == null ? super.getParticleIcon(data) : modelOf(facade).getParticleIcon(ModelData.EMPTY);
    }
}
