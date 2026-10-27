package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.core.HistorySnapshot;
import io.github.iceean.serverchathistory.storage.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path directory;
    private JsonHistoryStore store(Path path, List<Exception> errors) {
        return new JsonHistoryStore(path, snapshot -> { }, errors::add);
    }

    @Test void roundTripAndBackupRecoveryPreserveTimeAndSequence() throws Exception {
        var path = directory.resolve("world/data/history.json");
        var errors = new ArrayList<Exception>();
        var store = store(path, errors);
        var old = Fixtures.snapshot("old");
        store.save(old);
        assertEquals(old, store.load());
        store.save(Fixtures.snapshot("new"));
        Files.writeString(path, "corrupt");
        assertEquals(old, store.load());
        assertEquals(1, errors.size());
        try (var files = Files.list(path.getParent())) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith("history.json.corrupt-")));
        }
        store.save(old);
        assertEquals(old, store.load());
    }

    @Test void bothInvalidCopiesArePreservedUntilExplicitClear() throws Exception {
        var path = directory.resolve("history.json");
        Files.writeString(path, "null");
        Files.writeString(AtomicFiles.backup(path), "{\"version\":99}");
        var errors = new ArrayList<Exception>();
        var store = store(path, errors);
        assertEquals(HistorySnapshot.empty(), store.load());
        assertEquals(2, errors.size());
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
        store.reset(HistorySnapshot.empty());
        assertEquals(HistorySnapshot.empty(), store.load());
        assertEquals(HistorySnapshot.empty(), JsonSupport.GSON.fromJson(Files.readString(AtomicFiles.backup(path)), HistorySnapshot.class));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void invalidComponentsTriggerBackupRecovery() throws Exception {
        var path = directory.resolve("history.json");
        var basic = store(path, new ArrayList<>());
        basic.save(Fixtures.snapshot("valid"));
        basic.save(Fixtures.snapshot("invalid"));
        var errors = new ArrayList<Exception>();
        var checked = new JsonHistoryStore(path, snapshot -> {
            if (snapshot.entries().getFirst().message().renderedJson().contains("invalid")) throw new IllegalArgumentException("Invalid component");
        }, errors::add);
        assertEquals(Fixtures.snapshot("valid"), checked.load());
        assertEquals(1, errors.size());
    }

    @Test void worldDirectoriesDoNotShareData() throws Exception {
        var first = store(directory.resolve("one/history.json"), new ArrayList<>());
        var second = store(directory.resolve("two/history.json"), new ArrayList<>());
        first.save(Fixtures.snapshot("one"));
        assertTrue(second.load().entries().isEmpty());
    }

    @Test void clearDiscardsQueuedOldSnapshotsAndWaitsForInflightWrite() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = Collections.synchronizedList(new ArrayList<String>());
        HistoryStore slow = new HistoryStore() {
            public HistorySnapshot load() { return HistorySnapshot.empty(); }
            public void save(HistorySnapshot snapshot) throws IOException {
                String value = snapshot.entries().getFirst().message().renderedJson();
                if (value.contains("inflight")) {
                    started.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout"); }
                    catch (InterruptedException interrupted) { throw new IOException(interrupted); }
                }
                writes.add(value);
            }
            public void reset(HistorySnapshot snapshot) { assertTrue(snapshot.entries().isEmpty()); writes.add("clear"); }
        };
        try (var writer = new AsyncHistoryWriter(slow, failure -> fail(failure))) {
            writer.save(Fixtures.snapshot("inflight"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            writer.save(Fixtures.snapshot("queued old"));
            var clear = writer.clear(HistorySnapshot.empty());
            writer.save(Fixtures.snapshot("new after clear"));
            assertFalse(clear.isDone());
            release.countDown();
            clear.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertEquals(List.of("\"inflight\"", "clear", "\"new after clear\""), writes);
    }

    @Test void failedClearIsRetriedAsResetInsteadOfResurrectingBackup() throws Exception {
        var failFirst = new AtomicBoolean(true);
        var resets = new ArrayList<HistorySnapshot>();
        var errors = new ArrayList<Exception>();
        HistoryStore flaky = new HistoryStore() {
            public HistorySnapshot load() { return HistorySnapshot.empty(); }
            public void save(HistorySnapshot snapshot) { fail("A failed clear must force reset on the next save"); }
            public void reset(HistorySnapshot snapshot) throws IOException {
                if (failFirst.getAndSet(false)) throw new IOException("Disk full");
                resets.add(snapshot);
            }
        };
        try (var writer = new AsyncHistoryWriter(flaky, errors::add)) {
            assertThrows(ExecutionException.class, () -> writer.clear(HistorySnapshot.empty()).get(5, TimeUnit.SECONDS));
            assertNotNull(writer.lastFailure());
            writer.flush(Fixtures.snapshot("new")).get(5, TimeUnit.SECONDS);
            assertNull(writer.lastFailure());
        }
        assertEquals(List.of(Fixtures.snapshot("new")), resets);
        assertEquals(1, errors.size());
    }

    @Test void shutdownDrainsLastSnapshot() throws Exception {
        var path = directory.resolve("history.json");
        var errors = Collections.synchronizedList(new ArrayList<Exception>());
        var store = store(path, errors);
        try (var writer = new AsyncHistoryWriter(store, errors::add)) {
            for (int i = 0; i < 40; i++) writer.save(Fixtures.snapshot("message " + i));
            writer.flush(Fixtures.snapshot("final leave"));
        }
        assertEquals(Fixtures.snapshot("final leave"), store.load());
        assertTrue(errors.isEmpty());
    }

    @Test void pendingOrdinarySavesCoalesceAndRecoverAfterIoFailure() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new ArrayList<HistorySnapshot>();
        var errors = new ArrayList<Exception>();
        HistoryStore slow = new HistoryStore() {
            private boolean first = true;
            public HistorySnapshot load() { return HistorySnapshot.empty(); }
            public void save(HistorySnapshot snapshot) throws IOException {
                if (first) {
                    first = false;
                    started.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout"); }
                    catch (InterruptedException interrupted) { throw new IOException(interrupted); }
                    throw new IOException("Temporary storage failure");
                }
                writes.add(snapshot);
            }
            public void reset(HistorySnapshot snapshot) { fail("Ordinary saves must not reset recovery copies"); }
        };
        try (var writer = new AsyncHistoryWriter(slow, errors::add)) {
            writer.save(Fixtures.snapshot("inflight"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) writer.save(Fixtures.snapshot("queued " + i));
            assertEquals(2, writer.pendingJobs());
            release.countDown();
        } finally { release.countDown(); }
        assertEquals(List.of(Fixtures.snapshot("queued 19")), writes);
        assertEquals(1, errors.size());
    }
}
