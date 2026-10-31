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
        HistoryDisplay.display(item, abilities.canReceiveSystemMessages(), abilities.canReceivePlayerMessages(),
                senderRestricted, skipHistoryNarration, adapter, insert, narrate);
    }
}
