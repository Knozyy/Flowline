package com.knozyy.flowline.compat.jei;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.GhostTargets;
import com.knozyy.flowline.client.PipeScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JEI: drag items (and fluids) from the ingredient list onto filter slots. Only loaded by JEI. */
@JeiPlugin
public class FlowlineJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Flowline.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PipeScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(PipeScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
                Optional<ItemStack> item = ingredient.getItemStack();
                Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
                List<Target<I>> targets = new ArrayList<>();
                for (GhostTargets.Slot slot : GhostTargets.slots(gui, item.orElse(ItemStack.EMPTY),
                        fluid.orElse(FluidStack.EMPTY))) {
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
