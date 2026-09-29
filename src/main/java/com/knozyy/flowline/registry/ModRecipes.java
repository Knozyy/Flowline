package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.recipe.FacadeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Flowline.MODID);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<FacadeRecipe>> FACADE =
            SERIALIZERS.register("facade", () -> new SimpleCraftingRecipeSerializer<>(FacadeRecipe::new));

    private ModRecipes() {}
}
