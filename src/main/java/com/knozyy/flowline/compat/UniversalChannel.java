package com.knozyy.flowline.compat;

import com.knozyy.flowline.pipe.OffhandMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a universal pipe in the off hand stand in for the native Curvy pipe of the channel the player picked. Native
 * Curvy identifies the pipe being used through {@code CommonHandler.itemId(ItemStack)}; the mixins call
 * {@link #pending} right before native asks, and {@link #rewrite} answers with the channel's native item id.
 * The pending marker is consumed by the next {@code itemId} call, so it never leaks into unrelated lookups.
 */
public final class UniversalChannel {
    private record Pending(Player player, InteractionHand hand) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private UniversalChannel() {}

    public static void pending(Player player, InteractionHand hand) {
        PENDING.set(new Pending(player, hand));
    }

    public static void clear() {
        PENDING.remove();
    }

    /** The id native Curvy should see for {@code stack}; {@code original} unless this is a Curvy-mode universal pipe. */
    public static int rewrite(ItemStack stack, int original) {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (pending == null || pending.hand() != InteractionHand.OFF_HAND || !CurvyPipesCompat.universal(stack)) return original;
        Player player = pending.player();
        if (player.level().isClientSide) return Client.curvy() ? CurvyPipesCompat.nativeId(stack, Client.channel()) : original;
        return OffhandMode.of(player) == OffhandMode.CURVY
                ? CurvyPipesCompat.nativeId(stack, OffhandMode.channelOf(player)) : original;
    }

    /** Client-only state, kept in its own class so a dedicated server never loads it. */
    private static final class Client {
        static boolean curvy() { return com.knozyy.flowline.client.OffhandPipe.mode() == OffhandMode.CURVY; }
        static int channel() { return com.knozyy.flowline.client.OffhandPipe.channel(); }
    }
}
