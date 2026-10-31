package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.network.*;
import io.github.iceean.serverchathistory.storage.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistorySessionTest {
    @TempDir Path directory;
    private final List<String> calls = new ArrayList<>();
    private final UUID player = new UUID(1, 1);
    private ReplayPreferences preferences = ReplayPreferences.DEFAULT;
    private boolean failSend;
    private HistorySession<UUID> session;
    private volatile HistorySnapshot saved;

    @BeforeEach void setup() {
        var config = new ConfigManager(directory.resolve("config.json"), error -> fail(error));
        var store = new HistoryStore() {
            public HistorySnapshot load() { return HistorySnapshot.empty(); }
            public void save(HistorySnapshot snapshot) { saved = snapshot; }
            public void reset(HistorySnapshot snapshot) { saved = snapshot; }
        };
        var platform = new ReplayPlatform<UUID>() {
            public UUID player(UUID id) { return id.equals(player) ? id : null; }
            public ReplayDelivery delivery(UUID id, ModConfig config) { return new ReplayDelivery(preferences, true, false, "enhanced"); }
            public boolean visible(UUID id, HistoryEntry entry) { return entry.sequence() != 2; }
            public void send(UUID id, List<HistoryEntry> entries, boolean enhanced) {
                calls.add("send:" + entries.stream().map(HistoryEntry::sequence).toList());
                session.record(Fixtures.message(MessageKind.JOIN, "recursive"));
                if (failSend) throw new IllegalStateException("display failed");
            }
            public void boundary(UUID id, boolean start, int count) { calls.add("boundary:" + start + ":" + count); }
            public void notice(UUID id, String key, boolean error, Object... args) { calls.add(key); }
            public void failed(UUID id, RuntimeException failure) { calls.add("failed"); }
            public void retainDecoded(HistorySnapshot snapshot) { calls.add("retain:" + snapshot.nextSequence()); }
            public void clearDecoded() { calls.add("clear"); }
        };
        session = new HistorySession<>(config, new HistoryBuffer(HistorySnapshot.empty(), 50),
                new AsyncHistoryWriter(store, error -> fail(error)), platform);
    }
    @AfterEach void close() { if (session != null) session.close(); }

    @Test void filtersBeforePersonalLimitAndCoalescesJoinsAndSnapshots() {
        preferences = new ReplayPreferences(true, 1, 15);
        session.joined(player); session.joined(player); session.joined(new UUID(2, 2));
        for (int i = 0; i < 3; i++) session.record(Fixtures.message(MessageKind.JOIN, "record"));
        session.tick();
        assertEquals(List.of("retain:4", "boundary:true:1", "start", "send:[3]", "end", "boundary:false:1"), calls);
        assertEquals(3, session.size());
        calls.clear(); session.tick(); assertTrue(calls.isEmpty());
    }
    @Test void failedReplayClosesBoundaryAndRestoresRecording() {
        failSend = true;
        session.record(Fixtures.message(MessageKind.JOIN, "before")); session.joined(player); session.tick();
        assertEquals(List.of("retain:2", "boundary:true:1", "start", "send:[1]", "boundary:false:1", "failed"), calls);
        session.record(Fixtures.message(MessageKind.LEAVE, "after"));
        assertEquals(2, session.size());
    }
    @Test void optOutSuppressesBoundariesAndNotices() {
        preferences = new ReplayPreferences(false, 0, 15);
        session.joined(player); session.tick(); assertTrue(calls.isEmpty());
    }
    @Test void closeFlushesUntickedRecordsAndReleasesDecodedState() {
        session.record(Fixtures.message(MessageKind.LEAVE, "final"));
        session.close();
        assertEquals(1, saved.entries().size()); assertEquals("clear", calls.get(calls.size() - 1));
        // Avoid a second close of the writer in cleanup.
        session = null;
    }
}
