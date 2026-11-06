package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.client.HistoryDisplay;
import io.github.iceean.serverchathistory.core.MessageKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryReceptionPolicyTest {
    @Test void hiddenMessagesAndBlockedSendersAreRejectedRegardlessOfKind() {
        for (var kind : MessageKind.values()) {
            assertFalse(HistoryDisplay.allowed(kind, false, true, false));
            assertFalse(HistoryDisplay.allowed(kind, true, true, true));
        }
    }
    @Test void playerRestrictionStillAllowsSystemEvents() {
        assertFalse(HistoryDisplay.allowed(MessageKind.CHAT, true, false, false));
        for (var kind : MessageKind.values()) if (kind != MessageKind.CHAT)
            assertTrue(HistoryDisplay.allowed(kind, true, false, false));
    }
    @Test void unrestrictedMessagesAreAllowed() {
        for (var kind : MessageKind.values()) assertTrue(HistoryDisplay.allowed(kind, true, true, false));
    }
}
