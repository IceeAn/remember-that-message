package io.github.iceean.serverchathistory.core;

import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, registry-independent data. No signature or session key is persisted. */
public record HistoryEntry(long sequence, Message message) {
    public HistoryEntry {
        if (sequence < 1) throw new IllegalArgumentException("Invalid message sequence");
        Objects.requireNonNull(message, "message");
    }

    public record Message(MessageKind kind, Instant time, String renderedJson, Chat chat) {
        public Message {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(time, "time");
            validateJson(renderedJson);
            if ((kind == MessageKind.CHAT) != (chat != null)) {
                throw new IllegalArgumentException("Chat metadata does not match message kind");
            }
        }
    }

    public record Chat(UUID senderId, String senderName, String text, String bodyJson,
                       String nameJson, String filterMaskJson, boolean senderFiltering) {
        public Chat {
            Objects.requireNonNull(senderId, "senderId");
            Objects.requireNonNull(senderName, "senderName");
            Objects.requireNonNull(text, "text");
            if (senderName.isBlank() || senderName.length() > 16 || text.length() > 256) {
                throw new IllegalArgumentException("Invalid vanilla player chat metadata");
            }
            validateJson(bodyJson);
            validateJson(nameJson);
            validateJson(filterMaskJson);
        }
    }

    private static void validateJson(String value) {
        if (JsonParser.parseString(Objects.requireNonNull(value, "component JSON")).isJsonNull()) {
            throw new IllegalArgumentException("Null component JSON");
        }
    }
}
