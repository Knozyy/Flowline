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
    /** Held while curved placement is in hand: the mouse wheel turns the option wheel. */
    public static final KeyMapping OPTIONS = new KeyMapping("key.flowline.options", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.flowline");
    /** Held while drawing a curved pipe: no snapping to nodes, edges or faces, only the grid in the air. */
    public static final KeyMapping NO_SNAP = new KeyMapping("key.flowline.no_snap", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.flowline");

    private Keys() {}
}
