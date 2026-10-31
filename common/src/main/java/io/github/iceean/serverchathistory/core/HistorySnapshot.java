package io.github.iceean.serverchathistory.core;

import java.util.List;

public record HistorySnapshot(int version, long nextSequence, List<HistoryEntry> entries) {
    public static final int VERSION = 2;

    public HistorySnapshot {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported history version: " + version);
        entries = List.copyOf(entries);
        long last = 0;
        for (HistoryEntry entry : entries) {
            if (entry.sequence() <= last) throw new IllegalArgumentException("Unordered history");
            last = entry.sequence();
        }
        if (nextSequence <= last || nextSequence < 1) throw new IllegalArgumentException("Invalid next sequence");
    }

    public static HistorySnapshot empty() { return new HistorySnapshot(VERSION, 1, List.of()); }
}
