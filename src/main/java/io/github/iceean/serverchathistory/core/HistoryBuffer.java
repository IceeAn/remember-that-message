package io.github.iceean.serverchathistory.core;

import java.util.ArrayDeque;
import java.util.List;

/** Confined to the server thread. Capacity never causes eager allocation. */
public final class HistoryBuffer {
    private final ArrayDeque<HistoryEntry> entries = new ArrayDeque<>();
    private long nextSequence;
    private long limit;

    public HistoryBuffer(HistorySnapshot snapshot, long limit) {
        entries.addAll(snapshot.entries());
        nextSequence = snapshot.nextSequence();
        resize(limit);
    }

    public void append(HistoryEntry.Message message) {
        long following = Math.incrementExact(nextSequence);
        entries.addLast(new HistoryEntry(nextSequence, message));
        nextSequence = following;
        trim();
    }

    public void resize(long newLimit) {
        if (newLimit < 1) throw new IllegalArgumentException("historyLimit must be positive");
        limit = newLimit;
        trim();
    }

    private void trim() { while (entries.size() > limit) entries.removeFirst(); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
    public HistorySnapshot snapshot() { return new HistorySnapshot(HistorySnapshot.VERSION, nextSequence, List.copyOf(entries)); }
}
