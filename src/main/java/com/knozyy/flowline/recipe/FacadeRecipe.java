package com.knozyy.flowline.recipe;

import com.knozyy.flowline.item.FacadeItem;
import com.knozyy.flowline.registry.ModItems;
import com.knozyy.flowline.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A blank facade and one full block, anywhere in the grid: a facade of that block. */
public class FacadeRecipe extends CustomRecipe {
    public FacadeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Nullable
    private static BlockState find(CraftingInput input) {
        boolean blank = false;
        BlockState state = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.FACADE.get()) && FacadeItem.stateOf(stack) == null) {
                if (blank) return null;
                blank = true;
            } else {
                BlockState candidate = FacadeItem.stateFor(stack);
                if (candidate == null || state != null) return null;
                state = candidate;
            }
        }
        return blank ? state : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        BlockState state = find(input);
        return state == null ? ItemStack.EMPTY : FacadeItem.of(state);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FACADE.get();
    }
}
