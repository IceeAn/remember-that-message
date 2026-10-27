package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.core.*;
import java.time.Instant;
import java.util.List;

final class Fixtures {
    static HistoryEntry.Message message(MessageKind kind, String text) {
        return new HistoryEntry.Message(kind, Instant.parse("2026-09-15T12:34:56Z"), "\"" + text + "\"", null);
    }
    static HistorySnapshot snapshot(String text) {
        return new HistorySnapshot(1, 2, List.of(new HistoryEntry(1, message(MessageKind.JOIN, text))));
    }
}
