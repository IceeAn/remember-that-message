package io.github.iceean.serverchathistory.storage;

import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.text.ComponentFormat;
import java.util.List;

/** 磁盘头独立于内存快照；每条仍只保存原生正文。 */
public record HistoryFile(int version, String minecraftVersion, ComponentFormat componentEncoding,
                          long nextSequence, List<HistoryEntry> entries) {
    public static final int VERSION = 3;
    public HistoryFile {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported file version");
        new HistoryEncoding(minecraftVersion, componentEncoding);
        entries = List.copyOf(entries);
        new HistorySnapshot(HistorySnapshot.VERSION, nextSequence, entries);
    }
    public static HistoryFile of(HistorySnapshot snapshot, HistoryEncoding encoding) {
        return new HistoryFile(VERSION, encoding.minecraftVersion(), encoding.componentEncoding(), snapshot.nextSequence(), snapshot.entries());
    }
    public HistorySnapshot snapshot() { return new HistorySnapshot(HistorySnapshot.VERSION, nextSequence, entries); }
}
