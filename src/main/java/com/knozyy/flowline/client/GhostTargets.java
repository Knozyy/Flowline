package com.knozyy.flowline.client;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.item.ItemStack;
import com.knozyy.flowline.network.ModNetwork;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Filter slots of an open pipe screen that accept an item or fluid dragged from a recipe viewer (JEI, EMI). */
public final class GhostTargets {
    /** Screen area of one slot and what dropping there does. */
    public record Slot(int x, int y, Runnable drop) {}

    private GhostTargets() {}

    /** The rule a dragged ingredient becomes on this pipe, or null if it cannot be one. */
    @Nullable
    public static FilterEntry ruleFor(PipeMenu menu, ItemStack item, FluidStack fluid) {
        if (!menu.hasFilter()) return null;
        if (!fluid.isEmpty() && (menu.type == PipeType.FLUID || menu.type == PipeType.UNIVERSAL)) {
            return FilterEntry.fromFluid(fluid, menu.registries());
        }
        if (item.isEmpty()) return null;
        return FilterEntry.fromStack(menu.type, item, menu.registries());
    }

    public static List<Slot> slots(PipeScreen screen, ItemStack item, FluidStack fluid) {
        PipeMenu menu = screen.getMenu();
        FilterEntry rule = ruleFor(menu, item, fluid);
        if (rule == null) return List.of();
        List<Slot> slots = new ArrayList<>();
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (!(slot instanceof PipeMenu.GhostSlot ghost) || !ghost.isActive()) continue;
            int index = ghost.filterIndex();
            slots.add(new Slot(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, () ->
                    ModNetwork.sendToServer(new SetFilterEntryPayload(menu.containerId, index, Optional.of(rule)))));
        }
        return slots;
    }
}
