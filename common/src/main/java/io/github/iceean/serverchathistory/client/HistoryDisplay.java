package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.ProtocolData;
import java.util.function.Consumer;

public final class HistoryDisplay {
    private HistoryDisplay() { }
    public static <C> void display(ProtocolData.Item<C> item, boolean systemAllowed, boolean playerAllowed,
                                  boolean senderRestricted, boolean skipNarration, Consumer<Runnable> adapter,
                                  Consumer<C> insert, Consumer<C> narrate) {
        if (!systemAllowed || senderRestricted) return;
        if (item.kind() == MessageKind.CHAT && !playerAllowed) return;
        boolean[] inserted = {false};
        adapter.accept(() -> { insert.accept(item.content()); inserted[0] = true; });
        // Narration runs after metadata restoration and outside adapter failure handling.
        if (inserted[0] && !skipNarration) narrate.accept(item.content());
    }
}
