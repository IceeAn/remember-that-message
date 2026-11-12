package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.config.ModConfig.ReplayMode;
import dev.iceean.rememberthatmessage.core.ReplayPreferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPolicyTest {
    @Test void vanillaNeverNeedsNegotiationOrEnhancedPackets() {
        for (var mode : ReplayMode.values()) {
            var result = ReplayDelivery.resolve(null, false, mode);
            assertFalse(result.failed());
            assertFalse(result.enhanced());
            assertEquals(ReplayPreferences.DEFAULT, result.preferences());
        }
    }
    @Test void forcedSystemRetainsAcceptedPersonalPreferencesIncludingOptOut() {
        var preferences = new ReplayPreferences(false, 27, 3);
        var state = new Negotiation(); state.start(0); state.reply(ProtocolData.VERSION, preferences);
        var enhanced = ReplayDelivery.resolve(state, true, ReplayMode.AUTO);
        assertTrue(enhanced.enhanced()); assertEquals(preferences, enhanced.preferences());
        var system = ReplayDelivery.resolve(state, true, ReplayMode.SYSTEM);
        assertFalse(system.failed()); assertFalse(system.enhanced());
        assertEquals(preferences, system.preferences()); assertEquals("forced_system", system.reason());
    }
    @Test void incompleteChannelsAndTimeoutFallBackToServerDefaultsWithFailureReason() {
        var state = new Negotiation(); state.start(0);
        state.reply(ProtocolData.VERSION, new ReplayPreferences(false, 27, 3));
        var missing = ReplayDelivery.resolve(state, false, ReplayMode.AUTO);
        assertTrue(missing.failed()); assertFalse(missing.enhanced());
        assertEquals("missing_channel", missing.reason()); assertEquals(ReplayPreferences.DEFAULT, missing.preferences());
        var timeout = new Negotiation(); timeout.start(0); timeout.tick(2_000_000_000L);
        var result = ReplayDelivery.resolve(timeout, true, ReplayMode.AUTO);
        assertTrue(result.failed()); assertEquals("timeout", result.reason());
        assertEquals(ReplayPreferences.DEFAULT, result.preferences());
    }
}
