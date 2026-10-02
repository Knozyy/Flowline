package com.knozyy.flowline.client;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.SetFilterEntryPayload;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.item.ItemStack;
import com.knozyy.flowline.network.ModNetwork;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Places that accept an item or fluid dragged from a recipe viewer (JEI, EMI): the rule list of an open pipe
 * screen here, and the sample, tag list and mod box of the rule editor ({@link RuleEditorScreen#dropTargets}).
 */
public final class GhostTargets {
    /** Screen area of one drop target and what dropping there does. */
    public record Slot(int x, int y, int width, int height, Runnable drop) {}

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

    /** The whole rule list of a pipe screen: a dropped ingredient becomes a rule in the first free position. */
    public static List<Slot> slots(PipeScreen screen, ItemStack item, FluidStack fluid) {
        PipeMenu menu = screen.getMenu();
        FilterEntry rule = ruleFor(menu, item, fluid);
        int[] area = screen.ruleDropArea();
        if (rule == null || area == null) return List.of();
        return List.of(new Slot(area[0], area[1], area[2], area[3], () ->
                ModNetwork.sendToServer(new SetFilterEntryPayload(menu.containerId, -1, Optional.of(rule)))));
    }
}
