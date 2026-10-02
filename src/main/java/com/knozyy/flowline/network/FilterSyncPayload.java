package com.knozyy.flowline.network;

import com.knozyy.flowline.filter.FilterEntry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server → client: the rules of the open pipe menu's filter that changed since the last sync. A large or freshly
 * opened filter arrives as several of these; the first one of a fresh sync has {@code reset} set.
 */
public record FilterSyncPayload(int containerId, boolean reset, List<Change> changes) {
    public static final int MAX_CHANGES = 64;
    /** Highest rule position a sync may name; stored filters are cut there when loaded too. */
    public static final int MAX_INDEX = 1024;

    /** The rule now at {@code index}, or empty if that position was cleared. */
    public record Change(int index, Optional<FilterEntry> entry) {}

    public void encode(FriendlyByteBuf buf) {
        if (changes.size() > MAX_CHANGES) throw new IllegalArgumentException("Filter sync exceeds bound");
        buf.writeVarInt(containerId);
        buf.writeBoolean(reset);
        buf.writeVarInt(changes.size());
        for (Change change : changes) {
            buf.writeVarInt(change.index());
            buf.writeBoolean(change.entry().isPresent());
            change.entry().ifPresent(e -> e.write(buf));
        }
    }

    public static FilterSyncPayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean reset = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_CHANGES) throw new DecoderException("Filter sync exceeds bound");
        List<Change> changes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int index = buf.readVarInt();
            if (index < 0 || index > MAX_INDEX) throw new DecoderException("Filter sync index out of bounds");
            changes.add(new Change(index, buf.readBoolean() ? Optional.ofNullable(FilterEntry.read(buf)) : Optional.empty()));
        }
        return new FilterSyncPayload(containerId, reset, changes);
    }
}
