package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.Map;

@EventBusSubscriber(modid = Flowline.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Tint of the band around an undyed pipe's core. */
    private static final int UNDYED = 0xFFB9BEC7;

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PIPE.get(), PipeScreen::new);
    }

    /**
     * Tint index 0 is the band around the pipe core, drawn in the pipe's dye colour. Behind a facade the facade
     * block's own tints apply (grass, leaves...).
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        Block[] pipes = ModBlocks.pipes().stream().map(DeferredBlock::get).toArray(Block[]::new);
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) {
                return tintIndex == 0 ? UNDYED : -1;
            }
            BlockState facade = be.facade();
            if (facade != null) return Minecraft.getInstance().getBlockColors().getColor(facade, level, pos, tintIndex);
            if (tintIndex != 0) return -1;
            return be.color() == PipeBlockEntity.NO_COLOR ? UNDYED
                    : DyeColor.byId(be.color()).getTextureDiffuseColor();
        }, pipes);
    }

    /** Wraps every pipe model so a facade can replace it. */
    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (DeferredBlock<PipeBlock> pipe : ModBlocks.pipes()) {
            for (BlockState state : pipe.get().getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(location);
                if (model != null && !(model instanceof FacadeModel)) models.put(location, new FacadeModel(model));
            }
        }
    }
}
