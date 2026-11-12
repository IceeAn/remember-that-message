package dev.iceean.rememberthatmessage.minecraft;

import dev.iceean.rememberthatmessage.core.HistoryEntry;
import dev.iceean.rememberthatmessage.core.HistorySnapshot;
import dev.iceean.rememberthatmessage.network.PortableReplayEncoder;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Server-thread confined, world-local decoded data. Cached components are read-only. */
public final class ReplayMessageCache {
    private final MessageCodec codec;
    private final dev.iceean.rememberthatmessage.text.ConvertedMessageCache converted = new dev.iceean.rememberthatmessage.text.ConvertedMessageCache(GameTextFormat.CURRENT.componentEncoding());
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
        converted.retain(snapshot);
        long first = snapshot.entries().isEmpty() ? snapshot.nextSequence() : snapshot.entries().get(0).sequence();
        messages.keySet().removeIf(sequence -> sequence < first || sequence >= snapshot.nextSequence());
    }

    public void clear() { messages.clear(); converted.clear(); }
    public PortableReplayEncoder.Encoded encode(UUID session, HistoryEntry entry, boolean filtered, ComponentFormat target) {
        return PortableReplayEncoder.encode(session, entry, filtered, target, converted);
    }
    public dev.iceean.rememberthatmessage.text.TextConversion.Result converted(HistoryEntry entry, boolean filtered,
            dev.iceean.rememberthatmessage.text.ComponentFormat target) {
        return converted.get(entry, filtered, target);
    }
    int size() { return messages.size(); }

    static final class DecodedMessage {
        private final HistoryEntry.Message source;
        private final MessageCodec codec;
        private Component rendered;
        private Component filtered;

        private DecodedMessage(HistoryEntry.Message source, MessageCodec codec) {
            this.source = source;
            this.codec = codec;
        }

        Component rendered() {
            if (rendered == null) rendered = codec.decode(source.renderedJson());
            return rendered;
        }

        Component filtered() {
            if (filtered == null) filtered = codec.decode(source.chat().filteredJson());
            return filtered;
        }
    }
}
