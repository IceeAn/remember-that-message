package io.github.iceean.serverchathistory;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.text.*;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.storage.*;
import io.github.iceean.serverchathistory.network.PortableProtocol;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class NativeTextConversionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private final MessageCodec codec = new MessageCodec(RegistryAccess.EMPTY);
    @TempDir Path directory;
    private static String legacyClick(String action, String value) {
        var event = new JsonObject(); event.addProperty("action", action); event.addProperty("value", value);
        var component = new JsonObject(); component.addProperty("text", "可读正文"); component.addProperty("bold", true);
        component.add("clickEvent", event);
        return component.toString();
    }
    @Test void rejectedClickValuesStillDecodeAfterNativeAndWireConversion() {
        var target = GameTextFormat.CURRENT.componentEncoding();
        for (var pair : new String[][] {{"open_url", "example.com"}, {"open_url", "file:///tmp/test"},
                {"open_url", "https://exa mple.com"}, {"change_page", "0"}, {"change_page", "-1"},
                {"change_page", "abc"}, {"change_page", "2147483648"}, {"change_page", "1.5"},
                {"run_command", "/help§"}, {"suggest_command", "/help\n"}}) {
            String original = legacyClick(pair[0], pair[1]);
            for (var source : List.of(ComponentFormat.MC_1_20_1, ComponentFormat.MC_1_21_1)) {
                var converted = TextConversion.convert(original, source, target);
                assertReadableClick(converted, target.modern());
                String portable = TextConversion.convert(original, source, ComponentFormat.PORTABLE_V1).json();
                var item = new PortableProtocol.Item(UUID.randomUUID(), 1, MessageKind.JOIN, Instant.EPOCH,
                        ComponentFormat.PORTABLE_V1, portable, null, null, false);
                var received = (PortableProtocol.Item) PortableProtocol.decode(PortableProtocol.encode(item));
                assertReadableClick(TextConversion.convert(received.json(), received.format(), target), target.modern());
            }
        }
    }
    private void assertReadableClick(TextConversion.Result converted, boolean degraded) {
        var content = codec.decode(converted.json());
        assertEquals("可读正文", content.getString());
        assertTrue(content.getStyle().isBold());
        if (degraded) {
            assertNull(content.getStyle().getClickEvent());
            assertFalse(converted.losses().isEmpty());
        } else {
            assertNotNull(content.getStyle().getClickEvent());
            assertTrue(converted.losses().isEmpty());
        }
    }
    @Test void validClicksRemainAcceptedByActualTargetParser() {
        for (var pair : new String[][] {{"open_url", "https://example.com"}, {"open_url", "HTTP://example.com"},
                {"change_page", "1"}, {"change_page", "2147483647"}, {"run_command", "/say 你好"},
                {"suggest_command", "/help"}, {"copy_to_clipboard", "多行\n正文§"}}) {
            assertReadableClick(TextConversion.convert(legacyClick(pair[0], pair[1]), ComponentFormat.MC_1_20_1,
                    GameTextFormat.CURRENT.componentEncoding()), false);
        }
    }
    @Test void migrationPreservesRecordsFilteredBodyAndBackupDespiteUnsupportedClicks() throws Exception {
        for (int version : List.of(2, 3)) {
            Path file = directory.resolve("history-" + version + ".json");
            var chat = new HistoryEntry.Chat(UUID.randomUUID(), "Alice", true, HistoryEntry.Filtering.PARTIAL,
                    legacyClick("change_page", "0"));
            var entry = new HistoryEntry(1, new HistoryEntry.Message(MessageKind.CHAT, Instant.EPOCH,
                    legacyClick("open_url", "example.com"), chat));
            var snapshot = new HistorySnapshot(HistorySnapshot.VERSION, 2, List.of(entry));
            String original = version == 2 ? JsonSupport.GSON.toJson(snapshot)
                    : JsonSupport.GSON.toJson(HistoryFile.of(snapshot, new HistoryEncoding("1.20.1", ComponentFormat.MC_1_20_1)));
            Files.writeString(file, original);
            var errors = new ArrayList<Exception>();
            var store = new JsonHistoryStore(file, GameTextFormat.CURRENT, codec::validate, errors::add);
            var loaded = store.load();
            assertEquals(1, loaded.entries().size()); assertEquals(2, loaded.nextSequence());
            assertEquals(chat.senderId(), loaded.entries().get(0).message().chat().senderId());
            assertEquals(HistoryEntry.Filtering.PARTIAL, loaded.entries().get(0).message().chat().filtering());
            assertEquals(Instant.EPOCH, loaded.entries().get(0).message().time());
            assertNotNull(loaded.entries().get(0).message().chat().filteredJson());
            assertNull(store.blockedReason()); assertTrue(errors.isEmpty());
            if (GameTextFormat.CURRENT.componentEncoding().modern()) {
                assertEquals(1, store.migrationReport().degradedRecords());
                assertEquals(Set.of("click_value:open_url", "click_value:change_page"), store.migrationReport().losses());
                assertNull(codec.decode(loaded.entries().get(0).message().renderedJson()).getStyle().getClickEvent());
                assertNull(codec.decode(loaded.entries().get(0).message().chat().filteredJson()).getStyle().getClickEvent());
            }
            if (store.migrationReport() != null) assertEquals(original, Files.readString(store.migrationBackup()));
            store.save(loaded);
            assertEquals(loaded, store.load());
        }
    }
    @Test void everySourceConvertsIntoActualTargetCodec() {
        for(var source:ComponentFormat.values()) {
            String event=source.modern()?"\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/help\"},\"hover_event\":{\"action\":\"show_text\",\"value\":\"hint\"}":
                    "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/help\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"hint\"}";
            String json="{\"text\":\"hello\",\"color\":\"red\",\"bold\":true,"+event+",\"extra\":[{\"translate\":\"chat.type.text\",\"with\":[\"Alice\",\"你好\"]}]}";
            var converted=TextConversion.convert(json,source,GameTextFormat.CURRENT.componentEncoding());
            var decoded=codec.decode(converted.json());assertTrue(decoded.getString().startsWith("hello"));
            assertTrue(decoded.getStyle().isBold());assertNotNull(decoded.getStyle().getClickEvent());assertNotNull(decoded.getStyle().getHoverEvent());
            assertEquals(decoded,codec.decode(codec.encode(decoded)));
        }
    }
    @Test void actualNativeOutputSurvivesPortableRoundTrip() {
        Component input=Component.literal("hello").append(Component.translatable("chat.type.text", "Alice", "你好"));
        String nativeJson=codec.encode(input);var format=GameTextFormat.CURRENT.componentEncoding();
        String portable=TextConversion.convert(nativeJson,format,ComponentFormat.PORTABLE_V1).json();
        assertEquals(input,codec.decode(TextConversion.convert(portable,ComponentFormat.PORTABLE_V1,format).json()));
    }
}
