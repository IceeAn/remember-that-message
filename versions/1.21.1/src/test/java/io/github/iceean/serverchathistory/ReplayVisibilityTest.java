package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.minecraft.MessageCodec;
import io.github.iceean.serverchathistory.minecraft.ReplayMessageCache;
import io.github.iceean.serverchathistory.minecraft.VanillaReplayTransport;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Calls the production visibility entry point without starting a world or connection. */
class ReplayVisibilityTest {
    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    // Visibility filtering does not access the server or component codec.
    private final VanillaReplayTransport transport = new VanillaReplayTransport(new ReplayMessageCache(null));

    @BeforeAll static void bootstrapCodecs() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Stream<Arguments> permissionsAndVisibility() {
        return Stream.of(0, 1, 2, 3, 4)
                .flatMap(permissions -> Arrays.stream(ChatVisiblity.values())
                        .map(visibility -> Arguments.of(permissions, visibility)));
    }

    @ParameterizedTest
    @MethodSource("permissionsAndVisibility")
    void defaultPlayerAndOperatorLevelsFollowVanillaVisibility(int permissions, ChatVisiblity visibility) {
        var player = recipient(visibility, permissions, false, RECIPIENT);
        var chat = chat(1, FilterMask.PASS_THROUGH, false);
        var events = List.of(event(2, MessageKind.JOIN), event(3, MessageKind.LEAVE),
                event(4, MessageKind.DEATH), event(5, MessageKind.ADVANCEMENT));
        var history = List.of(chat, events.get(0), events.get(1), events.get(2), events.get(3));

        var expected = switch (visibility) {
            case FULL -> history;
            case SYSTEM -> events;
            case HIDDEN -> List.<HistoryEntry>of();
        };
        assertEquals(expected, transport.visibleTo(player, history));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void senderAndRecipientFilteringExcludeOnlyFullyFilteredChat(boolean senderFiltering, boolean recipientFiltering) {
        var player = recipient(ChatVisiblity.FULL, 0, recipientFiltering, RECIPIENT);
        var partialMask = new FilterMask(5);
        partialMask.setFiltered(1);
        var plain = chat(1, FilterMask.PASS_THROUGH, senderFiltering);
        var fullyFiltered = chat(2, FilterMask.FULLY_FILTERED, senderFiltering);
        var partial = chat(3, partialMask, senderFiltering);
        var join = event(4, MessageKind.JOIN);
        var history = List.of(plain, fullyFiltered, partial, join);

        var expected = senderFiltering || recipientFiltering ? List.of(plain, partial, join) : history;
        assertEquals(expected, transport.visibleTo(player, history));
    }

    @Test void ownFullyFilteredMessageRetainsVanillaSelfMessageException() {
        var player = recipient(ChatVisiblity.FULL, 0, true, SENDER);
        var history = List.of(chat(1, FilterMask.FULLY_FILTERED, true), event(2, MessageKind.JOIN));
        assertEquals(history, transport.visibleTo(player, history));
    }

    private static ServerPlayer recipient(ChatVisiblity visibility, int permissions,
                                          boolean filtering, UUID id) {
        // The test resource selects subclass mocking without an attached agent or world.
        var player = mock(ServerPlayer.class);
        when(player.getChatVisibility()).thenReturn(visibility);
        when(player.hasPermissions(anyInt())).thenAnswer(call -> permissions >= (int) call.getArgument(0));
        when(player.isTextFilteringEnabled()).thenReturn(filtering);
        when(player.getUUID()).thenReturn(id);
        return player;
    }

    private static HistoryEntry chat(long sequence, FilterMask mask, boolean senderFiltering) {
        var filtering = mask.isEmpty() ? HistoryEntry.Filtering.NONE : mask.isFullyFiltered() ? HistoryEntry.Filtering.FULL : HistoryEntry.Filtering.PARTIAL;
        var chat = new HistoryEntry.Chat(SENDER, "Alice", senderFiltering, filtering,
                filtering == HistoryEntry.Filtering.PARTIAL ? "\"<Alice> h#llo\"" : null);
        return new HistoryEntry(sequence, new HistoryEntry.Message(MessageKind.CHAT, Instant.EPOCH, "\"<Alice> hello\"", chat));
    }

    private static HistoryEntry event(long sequence, MessageKind kind) {
        return new HistoryEntry(sequence, Fixtures.message(kind, "public event"));
    }
}
