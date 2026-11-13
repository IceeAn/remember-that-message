package dev.iceean.rememberthatmessage;

import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.storage.*;
import dev.iceean.rememberthatmessage.text.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTest {
    @TempDir Path directory;
    private final List<Exception> errors = new ArrayList<>();
    private Path file() { return directory.resolve("history.json"); }
    private JsonHistoryStore store(ComponentFormat format) {
        return new JsonHistoryStore(file(), new HistoryEncoding(format.name(), format), snapshot -> {}, errors::add);
    }
    private HistorySnapshot snapshot(String rendered, String filtered) {
        var chat = new HistoryEntry.Chat(new UUID(1, 2), "Sender", true, HistoryEntry.Filtering.PARTIAL, filtered);
        return new HistorySnapshot(2, 8, List.of(new HistoryEntry(7, new HistoryEntry.Message(MessageKind.CHAT, Instant.EPOCH, rendered, chat))));
    }
    @Test void sameEncodingNeverConvertsOrCreatesMigrationBackup() throws Exception {
        var store = store(ComponentFormat.MC_26_3);
        var value = snapshot("{\"text\":\"raw\",\"unknown\":true}", "\"filtered\"");
        store.save(value); String original = Files.readString(file());
        assertEquals(value, store.load()); assertEquals(original, Files.readString(file()));
        assertNull(store.migrationReport()); assertFalse(Files.exists(store.migrationBackup()));
        assertTrue(original.contains("minecraftVersion")); assertTrue(original.contains("MC_26_3"));
    }
    @Test void allNativePairsMigrateAndKeepOnlyOneOldVersionCopy() throws Exception {
        for (var source : Arrays.stream(ComponentFormat.values()).filter(f -> f != ComponentFormat.PORTABLE_V1).toList()) {
            for (var target : Arrays.stream(ComponentFormat.values()).filter(f -> f != ComponentFormat.PORTABLE_V1).toList()) {
                if (source == target) continue;
                var value = snapshot("\"secret\"", "\"###\"");
                store(source).save(value); String original = Files.readString(file());
                var migrated = store(target); assertEquals(value, migrated.load());
                assertEquals(original, Files.readString(migrated.migrationBackup()));
                assertEquals(Files.readString(file()), Files.readString(AtomicFiles.backup(file())));
                assertEquals(0, migrated.migrationReport().degradedRecords());
                assertEquals(target, JsonSupport.read(Files.readString(file()), HistoryFile.class).componentEncoding());
                assertEquals(value, store(target).load());
                try (var files = Files.list(directory)) { assertEquals(3, files.count()); }
            }
        }
        assertTrue(errors.isEmpty());
    }
    @Test void lossyMigrationReportsCountAndPreservesFilteredAlternative() throws Exception {
        var value = snapshot("{\"text\":\"secret\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stone\",\"count\":2}}", "\"###\"");
        store(ComponentFormat.MC_26_3).save(value);
        var migrated = store(ComponentFormat.MC_1_20_1); var result = migrated.load();
        assertEquals(1, migrated.migrationReport().degradedRecords());
        assertTrue(migrated.migrationReport().losses().contains("hover:show_item"));
        assertEquals("\"###\"", result.entries().get(0).message().chat().filteredJson());
        assertTrue(result.entries().get(0).message().renderedJson().contains("minecraft:stone"));
        assertEquals(7, result.entries().get(0).sequence());
        assertEquals(Instant.EPOCH, result.entries().get(0).message().time());
    }
    @Test void unknownEncodingAndFutureVersionBlockOverwriteEvenWithHealthyBackup() throws Exception {
        for (String content : List.of("{\"version\":99}", "{\"version\":3,\"minecraftVersion\":\"future\",\"componentEncoding\":\"FUTURE\"}")) {
            Files.writeString(file(), content);
            Files.writeString(AtomicFiles.backup(file()), JsonSupport.GSON.toJson(Fixtures.snapshot("old")));
            var store = store(ComponentFormat.MC_26_3);
            assertEquals(HistorySnapshot.empty(), store.load()); assertNotNull(store.blockedReason());
            assertThrows(java.io.IOException.class, () -> store.save(Fixtures.snapshot("new")));
            assertEquals(content, Files.readString(file()));
            store.reset(HistorySnapshot.empty()); assertNull(store.blockedReason());
        }
    }
    @Test void targetValidationFailureRetainsOriginalAndNeverExposesUnfilteredText() throws Exception {
        store(ComponentFormat.MC_26_3).save(snapshot("\"secret\"", "\"###\""));
        String original = Files.readString(file());
        var rejected = new JsonHistoryStore(file(), new HistoryEncoding("1.20.1", ComponentFormat.MC_1_20_1),
                snapshot -> { throw new IllegalArgumentException("bad target"); }, errors::add);
        assertEquals(HistorySnapshot.empty(), rejected.load()); assertNotNull(rejected.blockedReason());
        assertEquals(original, Files.readString(file())); assertFalse(Files.exists(rejected.migrationBackup()));
    }
    @Test void unmarkedLegacySubsetUpgradesButAmbiguousComponentsRemainUntouched() throws Exception {
        Files.writeString(file(), JsonSupport.GSON.toJson(Fixtures.snapshot("old")));
        var store = store(ComponentFormat.MC_26_3); assertEquals(Fixtures.snapshot("old"), store.load());
        assertEquals("unmarked", store.migrationReport().sourceMinecraft());
        var unknown = snapshot("{\"score\":{\"name\":\"user\",\"objective\":\"x\"}}", "\"###\"");
        String original = JsonSupport.GSON.toJson(unknown); Files.writeString(file(), original);
        var blocked = store(ComponentFormat.MC_26_3); assertEquals(HistorySnapshot.empty(), blocked.load());
        assertNotNull(blocked.blockedReason()); assertEquals(original, Files.readString(file()));
    }
    @Test void migrationFromBackupDoesNotResurrectOldFormatOnRecovery() throws Exception {
        store(ComponentFormat.MC_1_20_1).save(Fixtures.snapshot("old"));
        Files.move(file(), AtomicFiles.backup(file()));
        var migrated = store(ComponentFormat.MC_26_3); assertEquals(Fixtures.snapshot("old"), migrated.load());
        Files.writeString(file(), "broken");
        assertEquals(Fixtures.snapshot("old"), store(ComponentFormat.MC_26_3).load());
    }
    @Test void invalidFilteredConversionStopsEntireMigrationWithOriginalRetained() throws Exception {
        var value = snapshot("\"secret\"", "{\"text\":\"###\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/help\"}}");
        store(ComponentFormat.MC_26_3).save(value); String original = Files.readString(file());
        var target = store(ComponentFormat.MC_1_20_1);
        assertEquals(HistorySnapshot.empty(), target.load()); assertNotNull(target.blockedReason());
        assertEquals(original, Files.readString(file()));
        assertThrows(java.io.IOException.class, () -> target.save(Fixtures.snapshot("new")));
    }
}
