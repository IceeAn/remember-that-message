package dev.iceean.rememberthatmessage;

import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.network.*;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryProtocolTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void preferencesAndStatusPreserveSnapshot() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var prefs = new ReplayPreferences(false, 10000, 5);
            var original = new HistoryProtocol.Preferences(HistoryProtocol.VERSION, prefs);
            HistoryProtocol.Preferences.CODEC.encode(bytes, original);
            assertEquals(original, HistoryProtocol.Preferences.CODEC.decode(bytes));
            bytes.clear();
            var status = new HistoryProtocol.Status(true, false, prefs, "forced_system");
            HistoryProtocol.Status.CODEC.encode(bytes, status);
            assertEquals(status, HistoryProtocol.Status.CODEC.decode(bytes));
        } finally { bytes.release(); }
    }
    @Test void everyKindPreservesTimeAndRichContentWithoutPlayerValidation() {
        for (var kind : MessageKind.values()) {
            var rich = Component.literal("historic").withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal("details"))));
            var item = new HistoryProtocol.Item(42, kind, Instant.parse("2020-01-01T00:00:00.123Z"), rich,
                    kind == MessageKind.CHAT ? UUID.randomUUID() : null, kind == MessageKind.CHAT ? "Alice" : null);
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                HistoryProtocol.Item.CODEC.encode(bytes, item);
                assertEquals(item, HistoryProtocol.Item.CODEC.decode(bytes));
                assertEquals(0, bytes.readableBytes());
            } finally { bytes.release(); }
        }
    }
    @Test void negotiationTimesOutWithoutLosingVanillaFallbackAndIgnoresLateReplies() {
        var state = new Negotiation(); state.start(100);
        assertFalse(state.tick(2_000_000_099L)); assertTrue(state.tick(2_000_000_100L));
        state.reply(HistoryProtocol.VERSION, new ReplayPreferences(false, 1, 0));
        assertFalse(state.accepted()); assertEquals(ReplayPreferences.DEFAULT, state.preferences());
        assertEquals("timeout", state.reason());
    }
    @Test void negotiationRejectsMismatchAndKeepsConnectionsIndependent() {
        var first = new Negotiation(); var second = new Negotiation(); first.start(0); second.start(0);
        first.reply(HistoryProtocol.VERSION, new ReplayPreferences(false, 20, 3));
        second.reply(HistoryProtocol.VERSION + 1, ReplayPreferences.DEFAULT);
        assertTrue(first.accepted()); assertFalse(first.preferences().receiveHistory());
        assertFalse(second.accepted()); assertEquals("version_mismatch", second.reason());
        var reconnect = new Negotiation(); assertFalse(reconnect.accepted());
        reconnect.reply(HistoryProtocol.VERSION, first.preferences()); assertFalse(reconnect.accepted());
    }
    @Test void rejectsInvalidSenderAndPreferences() {
        assertThrows(IllegalArgumentException.class, () -> new ReplayPreferences(true, -1, 15));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPreferences(true, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new HistoryProtocol.Item(1, MessageKind.CHAT, Instant.EPOCH, Component.empty(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new HistoryProtocol.Boundary(true, -1));
    }
}
