package com.knozyy.flowline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws a fluid as a square of its still texture, tinted like in the world (water is blue, not grey). */
public final class FluidIcon {
    private FluidIcon() {}

    public static void draw(GuiGraphics graphics, Fluid fluid, int x, int y, int size) {
        if (fluid == Fluids.EMPTY) return;
        FluidStack stack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ext.getStillTexture(stack));
        int tint = ext.getTintColor(stack);
        float alpha = ((tint >>> 24) & 0xFF) / 255f;
        RenderSystem.enableBlend();
        graphics.blit(x, y, 0, size, size, sprite, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f, alpha == 0 ? 1f : alpha);
        RenderSystem.disableBlend();
    }
}
