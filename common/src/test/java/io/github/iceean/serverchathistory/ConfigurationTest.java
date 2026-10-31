package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.text.Translations;
import java.nio.file.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    @TempDir Path directory;

    @Test void defaultsRoundTripAndMissingFieldsUseDefaults() {
        assertEquals(ModConfig.defaults(), ConfigCodec.read("{}"));
        assertEquals(ModConfig.defaults(), ConfigCodec.read(ConfigCodec.write(ModConfig.defaults())));
        assertEquals(Long.MAX_VALUE, ConfigCodec.with(ModConfig.defaults(), "historyLimit", Long.toString(Long.MAX_VALUE)).historyLimit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"historyLimit\":0}", "{\"historyLimit\":-1}", "{\"historyLimit\":1.5}",
            "{\"historyLimit\":9223372036854775808}", "{\"historyLimit\":\"50\"}", "{\"events\":null}",
            "{\"events\":{\"join\":\"false\"}}", "{\"replayMode\":\"AUTO\"}", "{\"typo\":true}", "{} trailing"})
    void invalidConfigurationsAreRejected(String json) { assertThrows(RuntimeException.class, () -> ConfigCodec.read(json)); }

    @Test void failedReloadRetainsEffectiveConfigAndPreservesBadFile() throws Exception {
        var file = directory.resolve("config.json");
        var errors = new ArrayList<Exception>();
        var manager = new ConfigManager(file, errors::add);
        assertTrue(manager.reload());
        manager.set("historyLimit", "7");
        Files.writeString(file, "{invalid");
        assertFalse(manager.reload());
        assertEquals(7, manager.active().historyLimit());
        assertTrue(manager.hasError());
        assertEquals("{invalid", Files.readString(file));
        manager.set("events.join", "false");
        assertFalse(manager.hasError());
        assertFalse(manager.active().events().join());
        assertEquals(1, errors.size());
    }

    @Test void startupFailureUsesDefaultsAndInvalidCommandChangesNothing() throws Exception {
        var file = directory.resolve("config.json");
        Files.writeString(file, "broken");
        var manager = new ConfigManager(file, failure -> { });
        assertFalse(manager.reload());
        assertEquals(ModConfig.defaults(), manager.active());
        assertThrows(RuntimeException.class, () -> manager.set("showEmptyHistory", "yes"));
        assertEquals("broken", Files.readString(file));
    }

    @Test void saveFailureDoesNotApplyCandidate() throws Exception {
        var parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        var manager = new ConfigManager(parent.resolve("config.json"), failure -> { });
        assertThrows(java.io.IOException.class, () -> manager.set("historyLimit", "3"));
        assertEquals(50, manager.active().historyLimit());
        assertTrue(manager.hasError());
    }

    @Test void onlySimplifiedChineseUsesChineseAndAllKeysHaveTranslations() throws Exception {
        assertNotEquals(Translations.text("en_us", "empty"), Translations.text("zh_cn", "empty"));
        assertEquals(Translations.text("en_us", "empty"), Translations.text("zh_tw", "empty"));
        assertEquals(Translations.text("en_us", "empty"), Translations.text(null, "empty"));
        assertTrue(Translations.text("en_us", "start", 12).contains("12"));
        for (var language : new String[]{"en_us", "zh_cn"}) {
            try (var input = getClass().getResourceAsStream("/assets/server_chat_history/lang/" + language + ".json")) {
                assertNotNull(input);
                var messages = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var key : new String[]{"start", "end", "empty", "config_error", "unavailable", "config_value", "config_saved", "config_reloaded",
                        "operation_failed", "clear_pending", "clear_done", "clear_failed", "status", "healthy", "error", "auto", "system", "negotiation_failed"}) {
                    assertTrue(messages.has(key), language + ": " + key);
                    assertFalse(messages.get(key).getAsString().isBlank());
                    assertEquals(messages.get(key).getAsString(), Translations.text(language, key));
                }
            }
        }
    }
}
