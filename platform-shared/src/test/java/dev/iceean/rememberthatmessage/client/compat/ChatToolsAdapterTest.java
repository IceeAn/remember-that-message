package dev.iceean.rememberthatmessage.client.compat;

import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.HistoryProtocol;
import java.time.*;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ChatToolsAdapterTest {
    private static final Instant OLD = Instant.parse("2020-07-01T12:34:56.789Z");
    private static HistoryProtocol.Item item(MessageKind kind, Instant time) {
        return new HistoryProtocol.Item(1, kind, time, Component.literal("history"),
                kind == MessageKind.CHAT ? java.util.UUID.randomUUID() : null, kind == MessageKind.CHAT ? "Alice" : null);
    }
    @ParameterizedTest @EnumSource(MessageKind.class)
    void allHistoryKindsUseCaptureTimeAndLocalZoneWithoutChangingLiveTime(MessageKind kind) {
        var adapter = new ChatToolsAdapter(() -> true, () -> { });
        var now = Instant.now();
        assertSame(now, adapter.instantOr(now)); assertEquals(42, adapter.epochSecondOr(42));
        assertNull(adapter.localTime(ZoneOffset.UTC));
        adapter.display(item(kind, OLD), () -> {
            assertEquals(OLD, adapter.instantOr(now)); assertEquals(OLD.getEpochSecond(), adapter.epochSecondOr(42));
            assertEquals(LocalDateTime.of(2020, 7, 1, 8, 34, 56, 789000000), adapter.localTime(ZoneId.of("America/New_York")));
            assertEquals(LocalDateTime.of(2020, 7, 1, 20, 34, 56, 789000000), adapter.localTime(ZoneId.of("Asia/Taipei")));
        });
        assertSame(now, adapter.instantOr(now)); assertNull(adapter.historyTime());
    }
    @Test void nestedScopesRestoreTimeEvenWhenInsertionThrowsWithoutRetrying() {
        var adapter = new ChatToolsAdapter(() -> true, () -> { });
        int[] calls = {0};
        adapter.display(item(MessageKind.CHAT, OLD), () -> {
            assertThrows(IllegalStateException.class, () -> adapter.display(item(MessageKind.JOIN, Instant.EPOCH), () -> {
                calls[0]++; assertEquals(Instant.EPOCH, adapter.historyTime()); throw new IllegalStateException("HUD error");
            }));
            assertEquals(OLD, adapter.historyTime());
        });
        assertEquals(1, calls[0]); assertNull(adapter.historyTime()); assertTrue(adapter.enabled());
    }
    @Test void equalTextQueuesRetainIndependentTimesAndResetRemovesStaleEntries() {
        var adapter = new ChatToolsAdapter(() -> true, () -> { });
        var first = Component.literal("same"); var second = Component.literal("same");
        assertEquals(first, second); assertNotSame(first, second);
        adapter.display(item(MessageKind.JOIN, OLD), () -> adapter.captureQueued(first));
        adapter.display(item(MessageKind.JOIN, Instant.EPOCH), () -> adapter.captureQueued(second));
        adapter.replayQueued(second, () -> assertEquals(Instant.EPOCH, adapter.historyTime()));
        adapter.replayQueued(first, () -> assertEquals(OLD, adapter.historyTime()));
        adapter.replayQueued(first, () -> assertNull(adapter.historyTime()));
        adapter.display(item(MessageKind.JOIN, OLD), () -> adapter.captureQueued(first));
        adapter.resetConnection(); adapter.replayQueued(first, () -> assertNull(adapter.historyTime()));
        assertNull(adapter.historyTime());
    }
    @Test void disabledAdapterDoesNotInitializeAndHookFailureFallsBackOncePerConnection() {
        var disabled = new ChatToolsAdapter(() -> false, () -> fail());
        disabled.display(item(MessageKind.JOIN, OLD), () -> assertNull(disabled.historyTime()));
        int[] initializations = {0}; int[] displays = {0}; var warnings = new ArrayList<String>();
        var adapter = new ChatToolsAdapter(() -> true, () -> { initializations[0]++; throw new LinkageError("hooks"); });
        adapter.onFailure(warnings::add);
        for (int i = 0; i < 2; i++) adapter.display(item(MessageKind.JOIN, OLD), () -> {
            displays[0]++; assertNull(adapter.historyTime());
        });
        assertEquals(1, initializations[0]); assertEquals(2, displays[0]); assertEquals(1, warnings.size());
        assertFalse(adapter.enabled());
        adapter.resetConnection(); adapter.display(item(MessageKind.JOIN, OLD), () -> displays[0]++);
        assertEquals(2, initializations[0]); assertEquals(3, displays[0]);
    }
}
