package dev.iceean.rememberthatmessage.core;

import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable display records; no signatures or game objects reach the writer. */
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
            if ((kind == MessageKind.CHAT) != (chat != null))
                throw new IllegalArgumentException("Chat metadata does not match message kind");
        }
    }

    public enum Filtering { NONE, PARTIAL, FULL }

    public record Chat(UUID senderId, String senderName, boolean senderFiltering,
                       Filtering filtering, String filteredJson) {
        public Chat {
            Objects.requireNonNull(senderId, "senderId");
            Objects.requireNonNull(senderName, "senderName");
            Objects.requireNonNull(filtering, "filtering");
            if (senderName.isBlank() || senderName.length() > 16)
                throw new IllegalArgumentException("Invalid sender name");
            if (filtering == Filtering.PARTIAL) validateJson(filteredJson);
            else if (filteredJson != null) throw new IllegalArgumentException("Unexpected filtered content");
        }
    }

    private static void validateJson(String value) {
        if (JsonParser.parseString(Objects.requireNonNull(value, "component JSON")).isJsonNull())
            throw new IllegalArgumentException("Null component JSON");
    }
}
