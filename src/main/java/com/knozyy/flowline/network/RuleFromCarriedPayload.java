package com.knozyy.flowline.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client → server: turn the stack the player carries in the open pipe menu into a rule at {@code index} (-1: the
 * first free position). The server builds the rule from its own copy of the carried stack, so a stack with a lot of
 * data never has to fit into a serverbound packet.
 */
public record RuleFromCarriedPayload(int containerId, int index) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(index);
    }

    public static RuleFromCarriedPayload decode(FriendlyByteBuf buf) {
        return new RuleFromCarriedPayload(buf.readVarInt(), buf.readVarInt());
    }
}
