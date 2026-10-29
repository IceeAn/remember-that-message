package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.config.ModConfig.ReplayMode;
import io.github.iceean.serverchathistory.core.ReplayPreferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPolicyTest {
    @Test void vanillaNeverNeedsNegotiationOrEnhancedPackets() {
        for (var mode : ReplayMode.values()) {
            var result = ServerHistoryNetworking.resolve(null, false, mode);
            assertFalse(result.failed());
            assertFalse(result.enhanced());
            assertEquals(ReplayPreferences.DEFAULT, result.preferences());
        }
    }
    @Test void forcedSystemRetainsAcceptedPersonalPreferencesIncludingOptOut() {
        var preferences = new ReplayPreferences(false, 27, 3);
        var state = new Negotiation(); state.start(0); state.reply(HistoryProtocol.VERSION, preferences);
        var enhanced = ServerHistoryNetworking.resolve(state, true, ReplayMode.AUTO);
        assertTrue(enhanced.enhanced()); assertEquals(preferences, enhanced.preferences());
        var system = ServerHistoryNetworking.resolve(state, true, ReplayMode.SYSTEM);
        assertFalse(system.failed()); assertFalse(system.enhanced());
        assertEquals(preferences, system.preferences()); assertEquals("forced_system", system.reason());
    }
    @Test void incompleteChannelsAndTimeoutFallBackToServerDefaultsWithFailureReason() {
        var state = new Negotiation(); state.start(0);
        state.reply(HistoryProtocol.VERSION, new ReplayPreferences(false, 27, 3));
        var missing = ServerHistoryNetworking.resolve(state, false, ReplayMode.AUTO);
        assertTrue(missing.failed()); assertFalse(missing.enhanced());
        assertEquals("missing_channel", missing.reason()); assertEquals(ReplayPreferences.DEFAULT, missing.preferences());
        var timeout = new Negotiation(); timeout.start(0); timeout.tick(2_000_000_000L);
        var result = ServerHistoryNetworking.resolve(timeout, true, ReplayMode.AUTO);
        assertTrue(result.failed()); assertEquals("timeout", result.reason());
        assertEquals(ReplayPreferences.DEFAULT, result.preferences());
    }
}
