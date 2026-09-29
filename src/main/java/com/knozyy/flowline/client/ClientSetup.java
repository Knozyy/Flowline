package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Flowline.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Tint of the band around an undyed pipe's core. */
    private static final int UNDYED = 0xFFB9BEC7;

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.PIPE.get(), PipeScreen::new));
    }

    /**
     * Tint index 0 is the band around the pipe core, drawn in the pipe's dye colour. Behind a facade the facade
     * block's own tints apply (grass, leaves...).
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        Block[] pipes = ModBlocks.pipes().stream().map(RegistryObject::get).toArray(Block[]::new);
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) {
                return tintIndex == 0 ? UNDYED : -1;
            }
            BlockState facade = be.facade();
            if (facade != null) return Minecraft.getInstance().getBlockColors().getColor(facade, level, pos, tintIndex);
            if (tintIndex != 0) return -1;
            if (be.color() == PipeBlockEntity.NO_COLOR) return UNDYED;
            float[] rgb = DyeColor.byId(be.color()).getTextureDiffuseColors();
            return (int) (rgb[0] * 255) << 16 | (int) (rgb[1] * 255) << 8 | (int) (rgb[2] * 255);
        }, pipes);
    }

    /** Wraps every pipe model so a facade can replace it. */
    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (RegistryObject<PipeBlock> pipe : ModBlocks.pipes()) {
            for (BlockState state : pipe.get().getStateDefinition().getPossibleStates()) {
                ResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(location);
                if (model != null && !(model instanceof FacadeModel)) models.put(location, new FacadeModel(model));
            }
        }
    }
}
