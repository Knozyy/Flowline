package com.knozyy.flowline.compat.emi;

import com.knozyy.flowline.client.GhostTargets;
import com.knozyy.flowline.client.PipeScreen;
import com.knozyy.flowline.client.RuleEditorScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/**
 * EMI: drag items (and fluids) from the index onto filter slots, and onto the rule editor's sample, tag list and mod
 * box. Only loaded by EMI.
 */
@EmiEntrypoint
public class FlowlineEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(PipeScreen.class, (screen, ingredient, x, y) ->
                drop(GhostTargets.slots(screen, item(ingredient), fluid(ingredient)), x, y));
        registry.addDragDropHandler(RuleEditorScreen.class, (screen, ingredient, x, y) ->
                drop(screen.dropTargets(item(ingredient), fluid(ingredient)), x, y));
    }

    private static boolean drop(List<GhostTargets.Slot> targets, int x, int y) {
        for (GhostTargets.Slot slot : targets) {
            if (new Bounds(slot.x(), slot.y(), slot.width(), slot.height()).contains(x, y)) {
                slot.drop().run();
                return true;
            }
        }
        return false;
    }

    private static FluidStack fluid(EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) return FluidStack.EMPTY;
        EmiStack stack = stacks.get(0);
        return stack.getKey() instanceof Fluid f ? new FluidStack(f, 1000, stack.getNbt()) : FluidStack.EMPTY;
    }

    private static ItemStack item(EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty() || !fluid(ingredient).isEmpty()) return ItemStack.EMPTY;
        return stacks.get(0).getItemStack();
    }
}
