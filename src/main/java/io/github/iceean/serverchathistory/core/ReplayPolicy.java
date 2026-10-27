package io.github.iceean.serverchathistory.core;

import io.github.iceean.serverchathistory.config.ModConfig;
import java.util.List;

/** An internal extension point for a future per-player offline cursor. */
public interface ReplayPolicy {
    List<HistoryEntry> select(HistorySnapshot history, ModConfig config);
    ReplayPolicy RECENT = (history, config) -> history.entries().stream()
            .filter(entry -> config.includes(entry.message().kind())).toList();

    static boolean usePlayerPackets(ModConfig config, boolean secureChatEnforced) {
        return config.replayMode() == ModConfig.ReplayMode.AUTO && !secureChatEnforced;
    }
}
