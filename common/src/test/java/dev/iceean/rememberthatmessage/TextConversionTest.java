package dev.iceean.rememberthatmessage;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.text.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.iceean.rememberthatmessage.text.ComponentFormat.*;
class TextConversionTest {
    private static String clickable(String action, String value) {
        var click = new com.google.gson.JsonObject();
        click.addProperty("action", action); click.addProperty("value", value);
        var root = new com.google.gson.JsonObject();
        root.addProperty("text", "可读正文"); root.addProperty("bold", true);
        root.add("clickEvent", click);
        root.add("extra", com.google.gson.JsonParser.parseString("[{\"text\":\"后续正文\",\"color\":\"red\"}]"));
        return root.toString();
    }
    @Test void invalidModernClickValuesLoseOnlyTheirInteraction() {
        for (var pair : new String[][] {
                {"open_url", "example.com"}, {"open_url", "ftp://example.com"},
                {"open_url", "https://exa mple.com"}, {"open_url", "https://%"}, {"open_url", ""},
                {"change_page", "0"}, {"change_page", "-1"}, {"change_page", "abc"},
                {"change_page", "2147483648"}, {"change_page", "1.5"},
                {"run_command", "/help§"}, {"run_command", "/help\n"},
                {"suggest_command", "/help\t"}, {"suggest_command", "/help\u007f"}}) {
            for (var source : List.of(MC_1_20_1, MC_1_21_1, PORTABLE_V1)) {
                String json = clickable(pair[0], pair[1]);
                var result = TextConversion.convert(json, source, MC_26_3);
                var root = com.google.gson.JsonParser.parseString(result.json()).getAsJsonObject();
                assertEquals("可读正文", root.get("text").getAsString());
                assertTrue(root.get("bold").getAsBoolean());
                assertEquals("后续正文", root.getAsJsonArray("extra").get(0).getAsJsonObject().get("text").getAsString());
                assertFalse(root.has("click_event"), Arrays.toString(pair));
                assertEquals(Set.of("click_value:" + pair[0]), result.losses());
            }
        }
    }
    @Test void validModernClickValuesArePreserved() {
        for (var pair : new String[][] {
                {"open_url", "https://example.com"}, {"open_url", "HTTP://example.com"},
                {"change_page", "1"}, {"change_page", "2147483647"},
                {"run_command", "/say 你好"}, {"suggest_command", "/help"},
                {"copy_to_clipboard", "多行\n正文§"}}) {
            var result = TextConversion.convert(clickable(pair[0], pair[1]), MC_1_20_1, MC_26_3);
            assertTrue(result.losses().isEmpty(), Arrays.toString(pair));
            assertEquals(pair[0], com.google.gson.JsonParser.parseString(result.json()).getAsJsonObject()
                    .getAsJsonObject("click_event").get("action").getAsString());
        }
    }
    @Test void olderTargetsAndPortableBodyKeepLegacyInteractionsUntilTargetConversion() {
        String json = clickable("change_page", "abc");
        assertEquals(json, TextConversion.convert(json, MC_1_20_1, MC_1_20_1).json());
        for (var target : List.of(MC_1_21_1, PORTABLE_V1)) {
            var result = TextConversion.convert(json, MC_1_20_1, target);
            assertTrue(result.losses().isEmpty());
            assertEquals("abc", com.google.gson.JsonParser.parseString(result.json()).getAsJsonObject()
                    .getAsJsonObject("clickEvent").get("value").getAsString());
        }
    }
    @Test void nestedTextStylesAndInteractionsRoundTrip() {
        String original = "{\"translate\":\"chat.type.text\",\"with\":[\"Alice\",{\"text\":\"hello\",\"bold\":true,\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/help\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"hint\",\"color\":\"red\"}}}]}";
        var modern = TextConversion.convert(original, MC_1_20_1, MC_26_3);
        assertTrue(modern.losses().isEmpty()); assertTrue(modern.json().contains("click_event"));
        var restored = TextConversion.convert(modern.json(), MC_26_3, MC_1_21_1);
        assertEquals(com.google.gson.JsonParser.parseString(original), com.google.gson.JsonParser.parseString(restored.json()));
    }
    @Test void unknownContentAndActionsDegradeLocally() {
        var value = TextConversion.convert("{\"text\":\"visible\",\"click_event\":{\"action\":\"custom\",\"id\":\"x\"},\"extra\":[{\"future_content\":\"unknown\"}]}", MC_26_3, PORTABLE_V1);
        assertTrue(value.json().contains("visible")); assertTrue(value.losses().contains("click:custom")); assertTrue(value.losses().contains("unsupported_content"));
    }
    @Test void conflictingMarkersAndDeepOrNullComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TextConversion.convert("{\"text\":\"a\",\"click_event\":{}}", MC_1_20_1, MC_26_3));
        assertThrows(IllegalArgumentException.class, () -> TextConversion.convert("null", MC_1_20_1, MC_26_3));
        assertThrows(IllegalArgumentException.class, () -> TextConversion.convert("[".repeat(66)+"\"a\""+"]".repeat(66), MC_1_20_1, MC_26_3));
    }
    @Test void lazyCacheSharesRecipientsSeparatesFilteringAndEvicts() {
        var cache = new ConvertedMessageCache(MC_26_3);
        var chat = new HistoryEntry.Chat(new UUID(1,2),"Alice",true,HistoryEntry.Filtering.PARTIAL,"\"###\"");
        var entry = new HistoryEntry(1,new HistoryEntry.Message(MessageKind.CHAT,Instant.EPOCH,"\"secret\"",chat));
        assertEquals("\"secret\"",cache.get(entry,false,MC_26_3).json()); assertEquals(0,cache.size());
        var plain=cache.get(entry,false,PORTABLE_V1);
        for(int i=0;i<100;i++) assertSame(plain,cache.get(entry,false,PORTABLE_V1));
        assertEquals("\"###\"",cache.get(entry,true,PORTABLE_V1).json()); assertEquals(2,cache.size());
        cache.retain(new HistorySnapshot(2,2,List.of())); assertEquals(0,cache.size());
        cache.get(entry,false,PORTABLE_V1);cache.clear();assertEquals(0,cache.size());
    }
    @Test void replacementIdentityNeverReusesOldBody() {
        var cache=new ConvertedMessageCache(MC_26_3);
        var first=Fixtures.snapshot("first").entries().get(0);var next=Fixtures.snapshot("next").entries().get(0);
        cache.get(first,false,PORTABLE_V1);assertEquals("\"next\"",cache.get(next,false,PORTABLE_V1).json());
    }
}
