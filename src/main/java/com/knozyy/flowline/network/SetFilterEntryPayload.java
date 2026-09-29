package com.knozyy.flowline.network;

import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;

/** Client → server: set (or clear, when empty) the filter rule at {@code index} of the open pipe menu. */
public record SetFilterEntryPayload(int containerId, int index, Optional<FilterEntry> entry) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(index);
        buf.writeBoolean(entry.isPresent());
        entry.ifPresent(e -> e.write(buf));
    }

    public static SetFilterEntryPayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int index = buf.readVarInt();
        Optional<FilterEntry> entry = buf.readBoolean() ? Optional.ofNullable(FilterEntry.read(buf)) : Optional.empty();
        return new SetFilterEntryPayload(containerId, index, entry);
    }
}
