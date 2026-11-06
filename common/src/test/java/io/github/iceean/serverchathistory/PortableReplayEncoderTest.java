package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.network.*;
import io.github.iceean.serverchathistory.text.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableReplayEncoderTest {
    private final UUID token = UUID.randomUUID();
    private static HistoryEntry entry(long sequence, String json) {
        return new HistoryEntry(sequence, new HistoryEntry.Message(MessageKind.JOIN, Instant.EPOCH, json, null));
    }
    private PortableProtocol.Item decode(PortableReplayEncoder.Encoded result) {
        return (PortableProtocol.Item) PortableProtocol.decode(result.bytes());
    }
    @Test void oversizedMiddleRecordBecomesPlaceholderWithoutLosingFollowingRecords() {
        String huge = "\"" + "x".repeat(TextConversion.MAX_TEXT_BYTES) + "\"";
        for (var source : List.of(ComponentFormat.MC_1_20_1, ComponentFormat.MC_1_21_1, ComponentFormat.MC_26_3)) {
            for (var target : List.of(source, ComponentFormat.PORTABLE_V1)) {
                var cache = new ConvertedMessageCache(source);
                var output = new ArrayList<PortableProtocol.Item>();
                for (var entry : List.of(entry(1, "\"first\""), entry(2, huge), entry(3, "\"last\""))) {
                    var encoded = PortableReplayEncoder.encode(token, entry, false, target, cache);
                    assertEquals(entry.sequence() == 2, encoded.failure() != null);
                    output.add(decode(encoded));
                }
                assertEquals(List.of(1L, 2L, 3L), output.stream().map(PortableProtocol.Item::sequence).toList());
                assertTrue(PortableReplayEncoder.isPlaceholder(output.get(1)));
                assertEquals("\"last\"", output.get(2).json());
                assertEquals(token, output.get(1).session());
                assertEquals(Instant.EPOCH, output.get(1).time());
                assertEquals(target, output.get(1).format());
                assertTrue(output.get(1).json().length() < 256);
            }
        }
    }
    @Test void nestingAndMalformedComponentShapesAreIsolated() {
        for (String json : List.of("[".repeat(66) + "\"deep\"" + "]".repeat(66),
                "{\"text\":\"visible\",\"click_event\":\"invalid\"}")) {
            var result = PortableReplayEncoder.encode(token, entry(1, json), false, ComponentFormat.PORTABLE_V1,
                    new ConvertedMessageCache(ComponentFormat.MC_26_3));
            assertNotNull(result.failure());
            assertTrue(PortableReplayEncoder.isPlaceholder(decode(result)));
        }
    }
    @Test void filteredFailureNeverFallsBackToUnfilteredTextAndKeepsReceptionMetadata() {
        var chat = new HistoryEntry.Chat(UUID.randomUUID(), "Alice", true, HistoryEntry.Filtering.PARTIAL,
                "\"" + "#".repeat(TextConversion.MAX_TEXT_BYTES) + "\"");
        var entry = new HistoryEntry(4, new HistoryEntry.Message(MessageKind.CHAT, Instant.ofEpochMilli(1234), "\"SECRET\"", chat));
        var cache = new ConvertedMessageCache(ComponentFormat.MC_26_3);
        var filtered = decode(PortableReplayEncoder.encode(token, entry, true, ComponentFormat.PORTABLE_V1, cache));
        assertTrue(PortableReplayEncoder.isPlaceholder(filtered));
        assertFalse(filtered.json().contains("SECRET"));
        assertEquals(MessageKind.CHAT, filtered.kind());
        assertEquals(chat.senderId(), filtered.senderId()); assertEquals(chat.senderName(), filtered.senderName());
        assertEquals(entry.message().time(), filtered.time());
        assertEquals("\"SECRET\"", decode(PortableReplayEncoder.encode(token, entry, false, ComponentFormat.PORTABLE_V1, cache)).json());
    }
    @Test void placeholderEncodingFailureEscapesRatherThanRetryingIndefinitely() {
        var invalidTime = new HistoryEntry(1, new HistoryEntry.Message(MessageKind.JOIN, Instant.MAX, "\"text\"", null));
        assertThrows(ArithmeticException.class, () -> PortableReplayEncoder.encode(token, invalidTime, false,
                ComponentFormat.MC_26_3, new ConvertedMessageCache(ComponentFormat.MC_26_3)));
    }
    @Test void ordinaryLossyConversionIsNotMisclassifiedAsAnUnavailableRecord() {
        var value = entry(1, "{\"text\":\"visible\",\"click_event\":{\"action\":\"custom\"}}");
        var result = PortableReplayEncoder.encode(token, value, false, ComponentFormat.PORTABLE_V1,
                new ConvertedMessageCache(ComponentFormat.MC_26_3));
        assertNull(result.failure()); assertFalse(result.losses().isEmpty());
        assertTrue(decode(result).degraded()); assertFalse(PortableReplayEncoder.isPlaceholder(decode(result)));
    }
}
