package com.knozyy.flowline.compat.jei;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.GhostTargets;
import com.knozyy.flowline.client.PipeScreen;
import com.knozyy.flowline.client.RuleEditorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI: how-to pages on Flowline's items, and dragging items (and fluids) from the ingredient list onto filter slots and
 * onto the rule editor's sample, tag list and mod box. Only loaded by JEI.
 */
@JeiPlugin
public class FlowlineJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Flowline.MODID, "jei");
    }

    /** How-to pages on Flowline's items (see {@link com.knozyy.flowline.compat.GuidePages}). */
    @Override
    public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
        for (var page : com.knozyy.flowline.compat.GuidePages.pages()) {
            registration.addItemStackInfo(page.items(), page.lines().toArray(net.minecraft.network.chat.Component[]::new));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PipeScreen.class, new Handler<PipeScreen>(GhostTargets::slots));
        registration.addGhostIngredientHandler(RuleEditorScreen.class,
                new Handler<RuleEditorScreen>(RuleEditorScreen::dropTargets));
    }

    @FunctionalInterface
    private interface Targets<T> {
        List<GhostTargets.Slot> find(T screen, ItemStack item, FluidStack fluid);
    }

    /** Hands JEI the drop areas {@code targets} finds on the screen for the dragged item or fluid. */
    private record Handler<T extends Screen>(Targets<T> targets) implements IGhostIngredientHandler<T> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
            // by class, so this works across JEI 15.x versions
            Object value = ingredient.getIngredient();
            ItemStack item = value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            FluidStack fluid = value instanceof FluidStack stack ? stack : FluidStack.EMPTY;
            List<Target<I>> result = new ArrayList<>();
            for (GhostTargets.Slot slot : targets.find(gui, item, fluid)) {
                result.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(slot.x(), slot.y(), slot.width(), slot.height());
                    }

                    @Override
                    public void accept(I value) {
                        slot.drop().run();
                    }
                });
            }
            return result;
        }

        @Override
        public void onComplete() {}
    }
}
