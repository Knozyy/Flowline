package com.knozyy.flowline.pipe;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a pipe in the off hand does on right-click: lay a route of block pipes to the player ("Build for me"), or place
 * a curved Curvy Pipes line. The client switches it with a key and tells the server, which keeps it per player.
 * Pipes in the main hand always place a single block.
 */
public enum OffhandMode {
    BUILD,
    CURVY;

    private static final Map<Player, OffhandMode> SERVER = new WeakHashMap<>();

    public OffhandMode next() {
        return this == BUILD ? CURVY : BUILD;
    }

    public static OffhandMode byOrdinal(int ordinal) {
        return ordinal == CURVY.ordinal() ? CURVY : BUILD;
    }

    /** Server: the mode {@code player} last chose; Build for me until the client says otherwise. */
    public static OffhandMode of(Player player) {
        return SERVER.getOrDefault(player, BUILD);
    }

    public static void set(Player player, OffhandMode mode) {
        SERVER.put(player, mode);
    }
}
