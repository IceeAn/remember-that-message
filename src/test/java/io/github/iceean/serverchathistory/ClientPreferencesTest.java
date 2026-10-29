package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.core.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClientPreferencesTest {
    @TempDir Path directory;
    @Test void defaultAndAllSettingsRoundTrip() {
        assertEquals(ClientConfig.DEFAULT, ClientConfig.read("{}"));
        var configured = ClientConfig.DEFAULT.with("historyLimit", "100").with("events.join", "false")
                .with("messageTimeIntegration", "false").with("receiveHistory", "false")
                .with("skipHistoryNarration", "false").with("chatHeadsIntegration", "false");
        assertEquals(configured, ClientConfig.read(configured.write()));
        assertEquals(100, configured.preferences().historyLimit());
        assertFalse(configured.preferences().includes(MessageKind.JOIN));
        assertTrue(configured.preferences().includes(MessageKind.CHAT));
        assertFalse(configured.skipHistoryNarration());
        assertEquals("false", configured.get("skipHistoryNarration"));
        assertFalse(configured.chatHeadsIntegration());
    }
    @Test void headsPreferenceIsIndependentOfTimeAndNegotiatedPreferences() {
        assertTrue(ClientConfig.read("{}").chatHeadsIntegration());
        var heads = ClientConfig.DEFAULT.with("messageTimeIntegration", "false");
        assertTrue(heads.chatHeadsIntegration());
        var time = ClientConfig.DEFAULT.with("chatHeadsIntegration", "false");
        assertTrue(time.messageTimeIntegration());
        assertEquals(heads.preferences(), time.preferences());
    }
    @Test void existingClientFilesDefaultToSkippingNarrationWithoutChangingNegotiatedPreferences() {
        var existing = ClientConfig.read("{\"historyLimit\":20,\"messageTimeIntegration\":false}");
        assertTrue(existing.skipHistoryNarration());
        assertEquals(existing.preferences(), existing.with("skipHistoryNarration", "false").preferences());
    }
    @Test void timeIntegrationUpdatesOnSaveAndReloadWithoutChangingNegotiatedPreferences() throws Exception {
        var file = directory.resolve("client.json");
        var store = new ClientConfigStore(file);
        assertTrue(store.reload()); assertTrue(store.active().messageTimeIntegration());
        var preferences = store.active().preferences();
        store.save(store.active().with("messageTimeIntegration", "false"));
        assertEquals("false", store.active().get("messageTimeIntegration"));
        assertTrue(store.reload()); assertFalse(store.active().messageTimeIntegration());
        assertEquals(preferences, store.active().preferences());
    }
    @Test void narrationPreferenceUpdatesLocallyOnSaveAndReload() throws Exception {
        var file = directory.resolve("client.json"); var store = new ClientConfigStore(file);
        assertTrue(store.reload()); assertTrue(store.active().skipHistoryNarration());
        store.save(store.active().with("skipHistoryNarration", "false"));
        assertFalse(store.active().skipHistoryNarration());
        Files.writeString(file, "{\"skipHistoryNarration\":true}");
        assertTrue(store.reload()); assertTrue(store.active().skipHistoryNarration());
        Files.writeString(file, "{\"skipHistoryNarration\":\"false\"}");
        assertFalse(store.reload()); assertTrue(store.active().skipHistoryNarration());
    }
    @Test void invalidSettingsDoNotReplaceActiveOrOverwriteFile() throws Exception {
        var file = directory.resolve("client.json"); var store = new ClientConfigStore(file);
        assertTrue(store.reload());
        store.save(ClientConfig.DEFAULT.with("historyLimit", "20"));
        var connection = store.active().preferences();
        Files.writeString(file, "{\"historyLimit\":-1}");
        assertFalse(store.reload()); assertNotNull(store.error());
        assertEquals(20, store.active().preferences().historyLimit());
        assertEquals("{\"historyLimit\":-1}", Files.readString(file));
        store.save(ClientConfig.DEFAULT.with("historyLimit", "40"));
        assertEquals(20, connection.historyLimit());
        assertNull(store.error());
    }
    @Test void rejectsFractionalOverflowAndWrongTypes() {
        for (String json : List.of("{\"historyLimit\":1.2}", "{\"historyLimit\":9223372036854775808}",
                "{\"receiveHistory\":\"false\"}", "{\"events\":{\"private\":true}}", "{\"unknown\":true}",
                "{\"skipHistoryNarration\":null}", "{\"skipHistoryNarration\":0}",
                "{\"messageTimeIntegration\":\"false\"}", "{\"messageTimeIntegration\":null}",
                "{\"messageTimeIntegration\":0}", "{\"chatHeadsIntegration\":0}",
                "{\"chatHeadsIntegration\":null}", "{\"chatHeadsIntegration\":\"true\"}"))
            assertThrows(RuntimeException.class, () -> ClientConfig.read(json));
    }
    @Test void filtersBeforeLimitAndKeepsOriginalOrder() {
        var history = List.of(entry(1, MessageKind.DEATH), entry(2, MessageKind.JOIN),
                entry(3, MessageKind.DEATH), entry(4, MessageKind.LEAVE), entry(5, MessageKind.DEATH));
        var prefs = new ReplayPreferences(true, 2, ReplayPreferences.bit(MessageKind.DEATH));
        assertEquals(List.of(history.get(2), history.get(4)), ReplaySelection.latest(history, prefs, e -> true));
        assertEquals(List.of(history.get(0), history.get(2)), ReplaySelection.latest(history, prefs, e -> e.sequence() != 5));
        assertTrue(ReplaySelection.latest(history, new ReplayPreferences(false, 0, 15), e -> { fail(); return true; }).isEmpty());
        assertTrue(ReplaySelection.latest(history, new ReplayPreferences(true, 0, 0), e -> true).isEmpty());
        assertEquals(history, ReplaySelection.latest(history, ReplayPreferences.DEFAULT, e -> true));
    }
    @Test void selectionStopsOnceLimitIsReached() {
        var history = List.of(entry(1, MessageKind.JOIN), entry(2, MessageKind.JOIN), entry(3, MessageKind.JOIN));
        int[] examined = {0};
        var result = ReplaySelection.latest(history, new ReplayPreferences(true, 1, 15), e -> { examined[0]++; return true; });
        assertEquals(1, examined[0]); assertEquals(3, result.getFirst().sequence());
    }
    private HistoryEntry entry(long id, MessageKind kind) { return new HistoryEntry(id, Fixtures.message(kind, "event")); }
}
