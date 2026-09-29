package com.knozyy.flowline.compat.emi;

import com.knozyy.flowline.client.GhostTargets;
import com.knozyy.flowline.client.PipeScreen;
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

/** EMI: drag items (and fluids) from the index onto filter slots. Only loaded by EMI. */
@EmiEntrypoint
public class FlowlineEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(PipeScreen.class, (screen, ingredient, x, y) -> {
            for (GhostTargets.Slot slot : targets(screen, ingredient)) {
                if (new Bounds(slot.x(), slot.y(), 16, 16).contains(x, y)) {
                    slot.drop().run();
                    return true;
                }
            }
            return false;
        });
    }

    private static List<GhostTargets.Slot> targets(PipeScreen screen, EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) return List.of();
        EmiStack stack = stacks.get(0);
        FluidStack fluid = stack.getKey() instanceof Fluid f
                ? new FluidStack(f, 1000, stack.getNbt()) : FluidStack.EMPTY;
        ItemStack item = fluid.isEmpty() ? stack.getItemStack() : ItemStack.EMPTY;
        return GhostTargets.slots(screen, item, fluid);
    }
}
