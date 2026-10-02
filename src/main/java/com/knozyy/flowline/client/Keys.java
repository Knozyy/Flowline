package com.knozyy.flowline.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Flowline's key bindings (Options > Controls > Key Binds > Flowline). */
public final class Keys {
    /** "Build for me": lay pipes from the looked-at block back to the player, with a pipe in the off hand. */
    public static final KeyMapping BUILD = new KeyMapping("key.flowline.build", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.flowline");

    private Keys() {}
}
