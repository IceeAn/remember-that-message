package io.github.iceean.serverchathistory.client.compat;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatHeadsAdapterTest {
    private static final UUID SENDER = UUID.randomUUID();
    private static HistoryProtocol.Item item(MessageKind kind) {
        return new HistoryProtocol.Item(1, kind, Instant.EPOCH, Component.literal("<Alice> hello"),
                kind == MessageKind.CHAT ? SENDER : null, kind == MessageKind.CHAT ? "Alice" : null);
    }
    private static class Backend implements ChatHeadsAdapter.Access {
        static final Object EMPTY = new Object();
        ChatHeadsAdapter.State state = new ChatHeadsAdapter.State("outer", "line", "refreshing line", true, false);
        PlayerInfo seen;
        boolean fail;
        int decorated;
        public ChatHeadsAdapter.State capture() { return state; }
        public void restore(ChatHeadsAdapter.State value) { state = value; }
        public void clear() { state = new ChatHeadsAdapter.State(EMPTY, EMPTY, EMPTY, false, state.serverSentUuid()); }
        public Component decorate(Component content, PlayerInfo player) {
            decorated++; seen = player;
            state = new ChatHeadsAdapter.State(player == null ? EMPTY : player, EMPTY, EMPTY, false, player != null);
            if (fail) throw new IllegalStateException("Broken adapter");
            return content.copy().append(player == null ? "" : " [head]");
        }
    }
    @Test void usesExactSenderAndOriginalRecordRemainsUnchanged() {
        var backend = new Backend(); var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        var player = mock(PlayerInfo.class); var message = item(MessageKind.CHAT);
        adapter.display(message, id -> { assertEquals(SENDER, id); return player; }, content -> {
            assertSame(player, backend.seen); assertSame(player, backend.state.last());
            assertFalse(backend.state.refreshing()); assertTrue(backend.state.serverSentUuid());
            assertEquals("<Alice> hello [head]", content.getString());
        });
        assertEquals("<Alice> hello", message.content().getString());
        assertSame(original, backend.state);
    }
    @Test void missingPlayerDelegatesToExistingDetectionWithoutCreatingAProfile() {
        var backend = new Backend(); var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        adapter.display(item(MessageKind.CHAT), id -> null, content -> assertEquals("<Alice> hello", content.getString()));
        assertNull(backend.seen); assertEquals(1, backend.decorated);
    }
    @ParameterizedTest @EnumSource(value = MessageKind.class, names = "CHAT", mode = EnumSource.Mode.EXCLUDE)
    void eventsDoNotBorrowPreviousHeadOrQueryPlayers(MessageKind kind) {
        var backend = new Backend(); var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        var event = item(kind);
        adapter.display(event, id -> { fail("Event lookup"); return null; }, content -> {
            assertSame(event.content(), content); assertSame(Backend.EMPTY, backend.state.last());
        });
        assertEquals(0, backend.decorated); assertSame(original, backend.state);
    }
    @Test void disabledIntegrationDoesNotInitializeOrLookUpPlayers() {
        var adapter = new ChatHeadsAdapter(() -> { fail(); return null; }, () -> false);
        var message = item(MessageKind.CHAT);
        adapter.display(message, id -> { fail(); return null; }, content -> assertSame(message.content(), content));
    }
    @Test void queuedMessagesUseIdentityAndRestoreHeadsWithoutDecoratingTwice() {
        var backend = new Backend() {
            @Override public Component decorate(Component content, PlayerInfo player) {
                super.decorate(content, player);
                return content; // BEFORE_LINE stores the head separately, even for identical text.
            }
        };
        var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        var alice = mock(PlayerInfo.class); var bob = mock(PlayerInfo.class);
        var messages = new ArrayList<Component>();
        for (var player : List.of(alice, bob)) adapter.display(item(MessageKind.CHAT), id -> player, content -> {
            messages.add(content); adapter.captureQueued(content);
        });
        assertEquals(messages.get(0), messages.get(1)); assertNotSame(messages.get(0), messages.get(1));
        adapter.replayQueued(messages.get(1), () -> assertSame(bob, backend.state.last()));
        assertSame(original, backend.state);
        adapter.replayQueued(messages.get(0), () -> assertSame(alice, backend.state.last()));
        assertSame(original, backend.state); assertEquals(2, backend.decorated);
        adapter.replayQueued(messages.get(0), () -> assertSame(original, backend.state));
    }
    @Test void resetDropsQueuedMetadataAndNestedScopesRestoreOuterHead() {
        var backend = new Backend(); var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        var alice = mock(PlayerInfo.class); var messages = new ArrayList<Component>();
        adapter.display(item(MessageKind.CHAT), id -> alice, content -> {
            var outer = backend.state;
            adapter.display(item(MessageKind.JOIN), id -> null, event -> assertSame(Backend.EMPTY, backend.state.last()));
            assertSame(outer, backend.state);
            messages.add(content); adapter.captureQueued(content);
        });
        adapter.resetConnection();
        adapter.replayQueued(messages.getFirst(), () -> assertSame(original, backend.state));
    }
    @Test void preparationFailureFallsBackOnceAndDisablesOnlyUntilReset() {
        var backend = new Backend(); backend.fail = true; var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        var warnings = new ArrayList<String>(); adapter.onFailure(warnings::add);
        var message = item(MessageKind.CHAT); var displayed = new ArrayList<Component>();
        adapter.display(message, id -> mock(PlayerInfo.class), displayed::add);
        assertEquals(List.of(message.content()), displayed); assertSame(original, backend.state);
        assertFalse(adapter.enabled()); assertEquals(1, warnings.size());
        backend.fail = false; adapter.resetConnection(); assertTrue(adapter.enabled());
        adapter.display(message, id -> null, displayed::add); assertEquals(2, backend.decorated);
    }
    @Test void insertionFailureRestoresStateAndNeverRetries() {
        var backend = new Backend(); var original = backend.state;
        var adapter = new ChatHeadsAdapter(() -> backend, () -> true);
        int[] attempts = {0};
        assertThrows(IllegalStateException.class, () -> adapter.display(item(MessageKind.CHAT), id -> null, content -> {
            attempts[0]++; throw new IllegalStateException("HUD failure");
        }));
        assertEquals(1, attempts[0]); assertSame(original, backend.state); assertTrue(adapter.enabled());
    }
}
