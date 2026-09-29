package com.knozyy.flowline.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: the "build for me" key was pressed; the server looks where the player looks itself. */
public record BuildPayload() {
    public void encode(FriendlyByteBuf buf) {}

    public static BuildPayload decode(FriendlyByteBuf buf) {
        return new BuildPayload();
    }
}
