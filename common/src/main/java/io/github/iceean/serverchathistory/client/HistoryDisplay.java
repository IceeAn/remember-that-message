package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.ProtocolData;
import java.util.function.Consumer;

public final class HistoryDisplay {
    private HistoryDisplay() { }
    public static boolean allowed(MessageKind kind, boolean systemAllowed, boolean playerAllowed, boolean senderRestricted) {
        return systemAllowed && !senderRestricted && (kind != MessageKind.CHAT || playerAllowed);
    }
    public static <C> void display(ProtocolData.Item<C> item, boolean systemAllowed, boolean playerAllowed,
                                  boolean senderRestricted, boolean skipNarration, Consumer<Runnable> adapter,
                                  Consumer<C> insert, Consumer<C> narrate) {
        if (!allowed(item.kind(), systemAllowed, playerAllowed, senderRestricted)) return;
        boolean[] inserted = {false};
        adapter.accept(() -> { insert.accept(item.content()); inserted[0] = true; });
        // Narration runs after metadata restoration and outside adapter failure handling.
        if (inserted[0] && !skipNarration) narrate.accept(item.content());
    }
}
