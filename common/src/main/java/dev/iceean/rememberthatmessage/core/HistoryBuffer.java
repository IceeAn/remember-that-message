package dev.iceean.rememberthatmessage.core;

import java.util.ArrayDeque;
import java.util.List;

/** Confined to the server thread. Capacity never causes eager allocation. */
public final class HistoryBuffer {
    private final ArrayDeque<HistoryEntry> entries = new ArrayDeque<>();
    private long nextSequence;
    private long limit;
    private HistorySnapshot cachedSnapshot;

    public HistoryBuffer(HistorySnapshot snapshot, long limit) {
        entries.addAll(snapshot.entries());
        nextSequence = snapshot.nextSequence();
        cachedSnapshot = snapshot;
        resize(limit);
    }

    public void append(HistoryEntry.Message message) {
        long following = Math.incrementExact(nextSequence);
        entries.addLast(new HistoryEntry(nextSequence, message));
        nextSequence = following;
        cachedSnapshot = null;
        trim();
    }

    public void resize(long newLimit) {
        if (newLimit < 1) throw new IllegalArgumentException("historyLimit must be positive");
        limit = newLimit;
        trim();
    }

    private void trim() {
        while (entries.size() > limit) {
            entries.removeFirst();
            cachedSnapshot = null;
        }
    }
    public void clear() { entries.clear(); cachedSnapshot = null; }
    public int size() { return entries.size(); }
    public HistorySnapshot snapshot() {
        if (cachedSnapshot == null) cachedSnapshot = new HistorySnapshot(HistorySnapshot.VERSION, nextSequence, List.copyOf(entries));
        return cachedSnapshot;
    }
}
