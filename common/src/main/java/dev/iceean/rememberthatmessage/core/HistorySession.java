package dev.iceean.rememberthatmessage.core;

import dev.iceean.rememberthatmessage.config.ConfigManager;
import dev.iceean.rememberthatmessage.storage.AsyncHistoryWriter;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Server-thread-confined lifecycle; only immutable snapshots reach the writer. */
public final class HistorySession<P> implements AutoCloseable {
    private final ConfigManager config;
    private final HistoryBuffer buffer;
    private final AsyncHistoryWriter writer;
    private final ReplayPlatform<P> platform;
    private final LinkedHashSet<UUID> pendingJoins = new LinkedHashSet<>();
    private final Map<UUID, List<HistoryEntry>> waiting = new LinkedHashMap<>();
    private boolean replaying;
    private boolean dirty;

    public HistorySession(ConfigManager config, HistoryBuffer buffer, AsyncHistoryWriter writer, ReplayPlatform<P> platform) {
        this.config = config;
        this.buffer = buffer;
        this.writer = writer;
        this.platform = platform;
    }
    public int size() { return buffer.size(); }
    public int visibleSize() { return ReplayPolicy.RECENT.select(buffer.snapshot(), config.active()).size(); }

    public void record(HistoryEntry.Message message) {
        if (replaying || message == null || !config.active().includes(message.kind())) return;
        buffer.append(message);
        dirty = true;
    }

    public void configChanged() {
        buffer.resize(config.active().historyLimit());
        var snapshot = buffer.snapshot();
        long first = snapshot.entries().isEmpty() ? snapshot.nextSequence() : snapshot.entries().get(0).sequence();
        waiting.replaceAll((id, entries) -> entries.stream().filter(entry -> entry.sequence() >= first).toList());
        saveSnapshot();
    }

    private void saveSnapshot() {
        platform.retainDecoded(buffer.snapshot());
        writer.save(buffer.snapshot());
        dirty = false;
    }

    public CompletableFuture<Void> clear() {
        buffer.clear();
        waiting.replaceAll((id, entries) -> List.of());
        platform.clearDecoded();
        dirty = false;
        return writer.clear(buffer.snapshot());
    }

    public void joined(UUID player) { waiting.remove(player); pendingJoins.add(player); }

    public void tick() {
        if (dirty) saveSnapshot();
        // JOIN fires partway through placeNewPlayer. End-of-tick runs after its join
        // announcement and player-info packets, without introducing a timer delay.
        var joined = pendingJoins.toArray(UUID[]::new);
        pendingJoins.clear();
        var entries = joined.length == 0 ? List.<HistoryEntry>of() : ReplayPolicy.RECENT.select(buffer.snapshot(), config.active());
        for (var id : joined) waiting.putIfAbsent(id, entries);
        boolean replayed = false;
        var iterator = waiting.entrySet().iterator();
        while (iterator.hasNext()) {
            var queued = iterator.next(); var id = queued.getKey();
            var player = platform.player(id);
            if (player == null) { iterator.remove(); continue; }
            if (!platform.ready(player)) continue;
            iterator.remove(); replayed = true;
            try { replay(player, queued.getValue()); }
            catch (RuntimeException failure) { platform.failed(id, failure); }
        }
        // 等待期间已被主缓冲淘汰的记录可能刚被解码；批次结束后释放这些缓存。
        if (replayed) platform.retainDecoded(buffer.snapshot());
    }

    private void replay(P player, List<HistoryEntry> history) {
        var delivery = platform.delivery(player, config.active());
        if (delivery.failed()) platform.notice(player, "negotiation_failed", true);
        if (!delivery.preferences().receiveHistory()) return;
        var entries = ReplaySelection.latest(history, delivery.preferences(), entry -> config.active().includes(entry.message().kind()) && platform.visible(player, entry));
        if (delivery.enhanced()) platform.boundary(player, true, entries.size());
        replaying = true;
        try {
            if (!entries.isEmpty()) {
                platform.notice(player, "start", false, entries.size());
                platform.send(player, entries, delivery.enhanced());
            }
            for (var notice : NoticePolicy.after(entries.size(), config.active(), config.hasError())) {
                platform.notice(player, notice.name().toLowerCase(java.util.Locale.ROOT), notice == NoticePolicy.Notice.CONFIG_ERROR);
            }
        } finally {
            replaying = false;
            if (delivery.enhanced()) platform.boundary(player, false, entries.size());
        }
    }

    @Override public void close() {
        pendingJoins.clear(); waiting.clear();
        try {
            writer.flush(buffer.snapshot());
            writer.close();
        } finally { platform.clearDecoded(); }
    }
}
