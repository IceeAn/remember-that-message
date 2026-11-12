package dev.iceean.rememberthatmessage.minecraft;

import dev.iceean.rememberthatmessage.core.HistoryEntry;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public interface ReplayTransport {
    List<HistoryEntry> visibleTo(ServerPlayer recipient, List<HistoryEntry> entries);
    void send(ServerPlayer recipient, List<HistoryEntry> entries, boolean enhanced);
}
