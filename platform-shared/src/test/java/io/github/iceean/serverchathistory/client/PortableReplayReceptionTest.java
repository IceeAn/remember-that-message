package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.network.*;
import io.github.iceean.serverchathistory.text.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PortableReplayReceptionTest {
    private final UUID session = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final MessageCodec codec = new MessageCodec(RegistryAccess.EMPTY);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private PortableProtocol.Item item(long sequence, String json) {
        return new PortableProtocol.Item(session, sequence, MessageKind.CHAT, Instant.ofEpochMilli(sequence),
                GameTextFormat.CURRENT.componentEncoding(), json, sender, "Alice", false);
    }
    private ClientPlaySession start(int count) {
        var state = new ClientPlaySession();
        state.hello(new PortableProtocol.Hello(session, 2, GameTextFormat.CURRENT.componentEncoding()));
        state.accept(new PortableProtocol.Status(session, true, ReplayPreferences.DEFAULT, "enhanced"));
        state.accept(new PortableProtocol.Boundary(session, true, count));
        return state;
    }
    @Test void nativePlaceholderParsesAndRetainsRestrictionsButNotPlayerPresentation() {
        var source = GameTextFormat.CURRENT.componentEncoding();
        var chat = new HistoryEntry.Chat(sender, "Alice", true, HistoryEntry.Filtering.NONE, null);
        var entry = new HistoryEntry(7, new HistoryEntry.Message(MessageKind.CHAT, Instant.ofEpochMilli(100),
                "\"" + "x".repeat(TextConversion.MAX_TEXT_BYTES) + "\"", chat));
        for (var target : List.of(source, ComponentFormat.PORTABLE_V1)) {
            var encoded = PortableReplayEncoder.encode(session, entry, false, target, new ConvertedMessageCache(source));
            var decoded = PortableItemDecoder.decode((PortableProtocol.Item) PortableProtocol.decode(encoded.bytes()), codec);
            assertTrue(decoded.placeholder()); assertFalse(decoded.simplified());
            assertEquals(MessageKind.CHAT, decoded.item().kind()); assertEquals(sender, decoded.item().senderId());
            assertEquals(7, decoded.item().sequence()); assertEquals(entry.message().time(), decoded.item().time());
            assertTrue(decoded.item().content().getString().contains("Chat History"));
            assertFalse(decoded.item().content().getString().contains("Alice"));
            var view = PortableItemDecoder.presentation(decoded.item(), true);
            assertNotEquals(MessageKind.CHAT, view.kind()); assertNull(view.senderId()); assertNull(view.senderName());
            assertSame(decoded.item().content(), view.content()); assertEquals(decoded.item().time(), view.time());
        }
    }
    @Test void localParseFailureGetsPlaceholderAndFollowingRecordsStillDisplay() {
        var state = start(3); var displayed = new ArrayList<String>();
        for (var wire : List.of(item(1, "\"first\""), item(2, "{invalid-json"), item(3, "\"last\""))) {
            var received = (PortableProtocol.Item) PortableProtocol.decode(PortableProtocol.encode(wire));
            assertTrue(state.accept(received));
            PortableItemDecoder.receive(received, state, true, () -> codec, decoded ->
                    HistoryDisplay.display(decoded.item(), true, true, false, true, Runnable::run,
                            content -> displayed.add(content.getString()), content -> fail("Narration should be skipped")));
        }
        assertEquals(3, displayed.size()); assertEquals("first", displayed.get(0)); assertEquals("last", displayed.get(2));
        assertTrue(displayed.get(1).contains("Chat History"));
        state.accept(new PortableProtocol.Boundary(session, false, 3));
        assertEquals(new ClientPlaySession.Incomplete(3, 3, 1, 3), state.takeIncomplete());
        assertNull(state.takeIncomplete());
    }
    @Test void unsupportedWireEncodingIsIsolatedToThatRecord() {
        var format = GameTextFormat.CURRENT.componentEncoding() == ComponentFormat.MC_26_3
                ? ComponentFormat.MC_1_20_1 : ComponentFormat.MC_26_3;
        var wrong = new PortableProtocol.Item(session, 1, MessageKind.JOIN, Instant.EPOCH, format, "\"text\"", null, null, false);
        assertTrue(PortableItemDecoder.decode(wrong, codec).placeholder());
        assertFalse(PortableItemDecoder.decode(item(2, "\"following\""), codec).placeholder());
    }
    private PortableProtocol.Item body(String type) {
        String json = switch (type) {
            case "placeholder" -> PortableReplayEncoder.PLACEHOLDER_JSON;
            case "invalid" -> "{invalid-json";
            default -> "\"hidden\"";
        };
        return new PortableProtocol.Item(session, 1, MessageKind.CHAT, Instant.EPOCH,
                GameTextFormat.CURRENT.componentEncoding(), json, sender, "Alice",
                type.equals("placeholder") || type.equals("simplified"));
    }
    @Test void filteredBodiesNeverDecodeOrReportFailureAndFollowingRecordsRemainVisible() {
        for (String type : List.of("valid", "placeholder", "invalid", "simplified")) {
            for (int mode = 0; mode < 3; mode++) {
                var state = start(2); var wire = body(type);
                assertTrue(state.accept(wire));
                boolean allowed = HistoryDisplay.allowed(wire.kind(), mode != 0, mode != 1, mode == 2);
                assertFalse(allowed);
                PortableItemDecoder.receive(wire, state, allowed,
                        () -> { throw new AssertionError("Rejected body must not even acquire a codec"); },
                        decoded -> fail("Rejected body must not display or signal simplification"));
                var following = item(2, "\"following\""); assertTrue(state.accept(following));
                var displayed = new ArrayList<String>();
                PortableItemDecoder.receive(following, state, true, () -> codec,
                        decoded -> displayed.add(decoded.item().content().getString()));
                assertEquals(List.of("following"), displayed);
                state.accept(new PortableProtocol.Boundary(session, false, 2));
                assertNull(state.takeIncomplete(), type + ":" + mode);
            }
        }
    }
    @Test void allowedBodiesStillReportPlaceholdersAndSignalSimplificationSeparately() {
        for (String type : List.of("valid", "placeholder", "invalid", "simplified")) {
            var state = start(1); var wire = body(type); assertTrue(state.accept(wire));
            var displayed = new ArrayList<PortableItemDecoder.Decoded>();
            PortableItemDecoder.receive(wire, state, true, () -> codec, displayed::add);
            assertEquals(1, displayed.size());
            assertEquals(type.equals("simplified"), displayed.get(0).simplified());
            state.accept(new PortableProtocol.Boundary(session, false, 1));
            boolean unavailable = type.equals("placeholder") || type.equals("invalid");
            assertEquals(unavailable ? new ClientPlaySession.Incomplete(1, 1, 1, 1) : null, state.takeIncomplete());
            assertNull(state.takeIncomplete());
        }
    }
    @Test void playerChatRestrictionDoesNotSuppressAllowedSystemEventFailures() {
        var state = start(1);
        var wire = new PortableProtocol.Item(session, 1, MessageKind.JOIN, Instant.EPOCH,
                GameTextFormat.CURRENT.componentEncoding(), "{invalid-json", null, null, false);
        assertTrue(state.accept(wire));
        boolean allowed = HistoryDisplay.allowed(wire.kind(), true, false, false);
        assertTrue(allowed);
        var displayed = new ArrayList<PortableItemDecoder.Decoded>();
        PortableItemDecoder.receive(wire, state, allowed, () -> codec, displayed::add);
        assertTrue(displayed.get(0).placeholder());
        state.accept(new PortableProtocol.Boundary(session, false, 1));
        assertEquals(new ClientPlaySession.Incomplete(1, 1, 1, 1), state.takeIncomplete());
    }
    @Test void displayFailureIsNotRetriedAsAnotherPlaceholder() {
        var state = start(1); var wire = item(1, "\"text\""); state.accept(wire);
        var inserted = new ArrayList<String>();
        assertThrows(IllegalStateException.class, () -> PortableItemDecoder.receive(wire, state, true, () -> codec,
                decoded -> HistoryDisplay.display(decoded.item(), true, true, false, false,
                Runnable::run, content -> { inserted.add(content.getString()); throw new IllegalStateException("after insertion"); },
                content -> fail("Failed display must not narrate"))));
        assertEquals(List.of("text"), inserted);
        state.accept(new PortableProtocol.Boundary(session, false, 1));
        assertEquals(new ClientPlaySession.Incomplete(1, 1, 1, 1), state.takeIncomplete());
    }
    @Test void allowedCodecAcquisitionFailureIsStillReported() {
        var state = start(1); var wire = item(1, "\"text\""); state.accept(wire);
        assertThrows(IllegalStateException.class, () -> PortableItemDecoder.receive(wire, state, true,
                () -> { throw new IllegalStateException("Missing world"); }, decoded -> fail("No display without a codec")));
        state.accept(new PortableProtocol.Boundary(session, false, 1));
        assertEquals(new ClientPlaySession.Incomplete(1, 1, 1, 1), state.takeIncomplete());
    }
}
