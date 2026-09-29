package com.knozyy.flowline.compat.jei;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.GhostTargets;
import com.knozyy.flowline.client.PipeScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** JEI: drag items (and fluids) from the ingredient list onto filter slots. Only loaded by JEI. */
@JeiPlugin
public class FlowlineJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Flowline.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PipeScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(PipeScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
                // by class, so this works across JEI 15.x versions
                Object value = ingredient.getIngredient();
                ItemStack item = value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
                FluidStack fluid = value instanceof FluidStack stack ? stack : FluidStack.EMPTY;
                List<Target<I>> targets = new ArrayList<>();
                for (GhostTargets.Slot slot : GhostTargets.slots(gui, item, fluid)) {
                    targets.add(new Target<>() {
                        @Override
                        public Rect2i getArea() {
                            return new Rect2i(slot.x(), slot.y(), 16, 16);
                        }

                        @Override
                        public void accept(I value) {
                            slot.drop().run();
                        }
                    });
                }
                return targets;
            }

            @Override
            public void onComplete() {}
        });
    }
}
