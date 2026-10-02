package com.knozyy.flowline.network;

import com.knozyy.flowline.filter.FilterEntry;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server → client: the filter rules on the visible page of the open pipe menu, so the editor can show them. */
public record FilterPagePayload(int containerId, int page, List<Optional<FilterEntry>> entries) {
    public static final int MAX_ENTRIES = 64;
    public void encode(FriendlyByteBuf buf) {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Filter page exceeds bound");
        buf.writeVarInt(containerId);
        buf.writeVarInt(page);
        buf.writeVarInt(entries.size());
        for (Optional<FilterEntry> entry : entries) {
            buf.writeBoolean(entry.isPresent());
            entry.ifPresent(e -> e.write(buf));
        }
    }

    public static FilterPagePayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int page = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new DecoderException("Filter page exceeds bound");
        List<Optional<FilterEntry>> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(buf.readBoolean() ? Optional.ofNullable(FilterEntry.read(buf)) : Optional.empty());
        return new FilterPagePayload(containerId, page, entries);
    }
}
