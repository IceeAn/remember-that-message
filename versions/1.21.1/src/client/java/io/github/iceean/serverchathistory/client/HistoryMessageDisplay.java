package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.network.chat.Component;

/** Applies client restrictions before any history display or accessibility side effects. */
final class HistoryMessageDisplay {
    private HistoryMessageDisplay() { }

    static boolean playerAllowed(ChatVisiblity visibility, Minecraft.ChatStatus status, boolean local) {
        return visibility == ChatVisiblity.FULL && status.isChatAllowed(local);
    }

    static void display(HistoryProtocol.Item item, boolean systemAllowed, boolean playerAllowed, boolean senderRestricted,
                        boolean skipHistoryNarration, Consumer<Runnable> adapter,
                        Consumer<Component> insert, Consumer<Component> narrate) {
        HistoryDisplay.display(item, systemAllowed, playerAllowed,
                senderRestricted, skipHistoryNarration, adapter, insert, narrate);
    }
}
