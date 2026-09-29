package com.knozyy.flowline.util;

import net.minecraft.world.item.ItemStack;

/** Small ItemStack helpers that behave the same on every supported Minecraft version. */
public final class Stacks {
    private Stacks() {}

    /** A copy of {@code stack} with {@code count} items (empty when count is 0 or less). */
    public static ItemStack withCount(ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
