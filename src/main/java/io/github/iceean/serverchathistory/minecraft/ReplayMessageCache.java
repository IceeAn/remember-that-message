package io.github.iceean.serverchathistory.minecraft;

import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.HistorySnapshot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;

/** Server-thread confined, world-local decoded data. Cached components are read-only. */
public final class ReplayMessageCache {
    private final MessageCodec codec;
    private final Map<Long, DecodedMessage> messages = new HashMap<>();

    public ReplayMessageCache(MessageCodec codec) { this.codec = codec; }

    DecodedMessage get(HistoryEntry entry) {
        var decoded = messages.get(entry.sequence());
        if (decoded == null || decoded.source != entry.message()) {
            decoded = new DecodedMessage(entry.message(), codec);
            messages.put(entry.sequence(), decoded);
        }
        return decoded;
    }

    /** Discard evicted history without decoding records that have never been replayed. */
    public void retain(HistorySnapshot snapshot) {
        long first = snapshot.entries().isEmpty() ? snapshot.nextSequence() : snapshot.entries().getFirst().sequence();
        messages.keySet().removeIf(sequence -> sequence < first || sequence >= snapshot.nextSequence());
    }

    public void clear() { messages.clear(); }
    int size() { return messages.size(); }

    static final class DecodedMessage {
        private final HistoryEntry.Message source;
        private final MessageCodec codec;
        private Component rendered;
        private Component body;
        private Component name;
        private FilterMask mask;

        private DecodedMessage(HistoryEntry.Message source, MessageCodec codec) {
            this.source = source;
            this.codec = codec;
        }

        Component rendered() {
            if (rendered == null) rendered = codec.decode(source.renderedJson());
            return rendered;
        }

        Component body() {
            if (body == null) body = codec.decode(source.chat().bodyJson());
            return body;
        }

        Component name() {
            if (name == null) name = codec.decode(source.chat().nameJson());
            return name;
        }

        FilterMask mask() {
            if (mask == null) mask = MessageCodec.decodeMask(source.chat().filterMaskJson());
            return mask;
        }
    }
}
