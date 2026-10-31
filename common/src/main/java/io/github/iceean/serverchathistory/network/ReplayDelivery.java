package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.config.ModConfig;
import io.github.iceean.serverchathistory.core.ReplayPreferences;

public record ReplayDelivery(ReplayPreferences preferences, boolean enhanced, boolean failed, String reason) {
    public static ReplayDelivery resolve(Negotiation state, boolean channels, ModConfig.ReplayMode mode) {
        if (state == null) return new ReplayDelivery(ReplayPreferences.DEFAULT, false, false, "vanilla");
        boolean ready = state.accepted() && channels;
        boolean enhanced = ready && mode == ModConfig.ReplayMode.AUTO;
        String reason = ready ? enhanced ? "enhanced" : "forced_system" : state.accepted() ? "missing_channel" : state.reason();
        var prefs = ready ? state.preferences() : ReplayPreferences.DEFAULT;
        return new ReplayDelivery(prefs, enhanced, !ready, reason);
    }
}
