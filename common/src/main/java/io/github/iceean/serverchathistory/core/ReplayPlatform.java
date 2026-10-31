package io.github.iceean.serverchathistory.core;

import io.github.iceean.serverchathistory.config.ModConfig;
import io.github.iceean.serverchathistory.network.ReplayDelivery;
import java.util.List;
import java.util.UUID;

/** World-bound game operations. Implementations are called only on the server thread. */
public interface ReplayPlatform<P> {
    P player(UUID id);
    ReplayDelivery delivery(P player, ModConfig config);
    boolean visible(P player, HistoryEntry entry);
    void send(P player, List<HistoryEntry> entries, boolean enhanced);
    void boundary(P player, boolean start, int count);
    void notice(P player, String key, boolean error, Object... args);
    void failed(UUID player, RuntimeException failure);
    void retainDecoded(HistorySnapshot snapshot);
    void clearDecoded();
}
