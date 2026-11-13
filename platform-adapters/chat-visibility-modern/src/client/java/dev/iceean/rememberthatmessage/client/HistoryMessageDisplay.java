package dev.iceean.rememberthatmessage.client;

import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.HistoryProtocol;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.network.chat.Component;

/** Applies client restrictions before any history display or accessibility side effects. */
final class HistoryMessageDisplay {
    private HistoryMessageDisplay() { }

    static boolean allowed(MessageKind kind, ChatAbilities abilities, boolean senderRestricted) {
        return HistoryDisplay.allowed(kind, abilities.canReceiveSystemMessages(), abilities.canReceivePlayerMessages(), senderRestricted);
    }

    static void display(HistoryProtocol.Item item, ChatAbilities abilities, boolean senderRestricted,
                        boolean skipHistoryNarration, Consumer<Runnable> adapter,
                        Consumer<Component> insert, Consumer<Component> narrate) {
        HistoryDisplay.display(item, abilities.canReceiveSystemMessages(), abilities.canReceivePlayerMessages(),
                senderRestricted, skipHistoryNarration, adapter, insert, narrate);
    }
}
