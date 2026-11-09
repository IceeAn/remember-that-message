package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.client.compat.*;
import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;

/** 仅封装随游戏 API 变化的注册、接收权限和显示操作。 */
final class GameClientPlatform {
    private GameClientPlatform() { }
    static void openSettings() { Minecraft.getInstance().setScreen(new HistoryConfigScreen(null)); }
    static void localMessage(Component message) { Minecraft.getInstance().gui.getChat().addMessage(message); }
    static boolean canReceive(MessageKind kind, java.util.UUID senderId) {
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null || client.getConnection() == null) return false;
        boolean senderRestricted = senderId != null && (client.isBlocked(senderId));
        return HistoryMessageDisplay.allowed(kind, client.options.chatVisibility().get() != ChatVisiblity.HIDDEN,
                HistoryMessageDisplay.playerAllowed(client.options.chatVisibility().get(), client.getChatStatus(), client.isLocalServer()), senderRestricted);
    }
    static void display(HistoryProtocol.Item payload, HistoryProtocol.Item presentation,
                        HistoryTimeIntegrations integrations, ChatHeadsAdapter heads) {
            var client = Minecraft.getInstance(); var player = client.player;
            if (player == null || client.getConnection() == null) return;
            boolean senderRestricted = payload.senderId() != null
                    && (client.isBlocked(payload.senderId()));
            HistoryMessageDisplay.display(payload, client.options.chatVisibility().get() != ChatVisiblity.HIDDEN,
                    HistoryMessageDisplay.playerAllowed(client.options.chatVisibility().get(), client.getChatStatus(), client.isLocalServer()), senderRestricted, HistoryClient.CONFIG.active().skipHistoryNarration(),
                    insert -> integrations.display(presentation, insert),
                    content -> heads.display(presentation, id -> client.getConnection().getPlayerInfo(id),
                            decorated -> client.gui.getChat().addMessage(decorated)),
                    GameNarration::say);
    }
}
