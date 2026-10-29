package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.network.chat.Component;

/** Applies client restrictions before any history display or accessibility side effects. */
final class HistoryMessageDisplay {
    private HistoryMessageDisplay() { }

    static void display(HistoryProtocol.Item item, ChatAbilities abilities, boolean senderRestricted,
                        boolean skipHistoryNarration, Consumer<Runnable> adapter,
                        Consumer<Component> insert, Consumer<Component> narrate) {
        if (!abilities.canReceiveSystemMessages() || senderRestricted) return;
        if (item.kind() == MessageKind.CHAT && !abilities.canReceivePlayerMessages()) return;

        boolean[] inserted = {false};
        adapter.accept(() -> {
            insert.accept(item.content());
            inserted[0] = true;
        });
        // Narration runs after metadata restoration, outside the optional adapter's error handling.
        if (inserted[0] && !skipHistoryNarration) narrate.accept(item.content());
    }
}
