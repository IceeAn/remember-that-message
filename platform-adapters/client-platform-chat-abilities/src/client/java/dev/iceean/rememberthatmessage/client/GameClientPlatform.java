package dev.iceean.rememberthatmessage.client;

import dev.iceean.rememberthatmessage.client.compat.*;
import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.HistoryProtocol;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 仅封装随游戏 API 变化的注册、接收权限和显示操作。 */
final class GameClientPlatform {
    private GameClientPlatform() { }
    static void setScreen(net.minecraft.client.gui.screens.Screen screen) { Minecraft.getInstance().setScreen(screen); }
    static void openSettings() { setScreen(new HistoryConfigScreen(null)); }
    static void localMessage(Component message) { Minecraft.getInstance().gui.getChat().addClientSystemMessage(message); }
    static boolean canReceive(MessageKind kind, java.util.UUID senderId) {
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null || client.getConnection() == null) return false;
        boolean senderRestricted = senderId != null && client.isBlocked(senderId);
        return HistoryMessageDisplay.allowed(kind, player.chatAbilities(), senderRestricted);
    }
    static void display(HistoryProtocol.Item payload, HistoryProtocol.Item presentation,
                        HistoryTimeIntegrations integrations, ChatHeadsAdapter heads) {
            var client = Minecraft.getInstance(); var player = client.player;
            if (player == null || client.getConnection() == null) return;
            boolean senderRestricted = payload.senderId() != null
                    && client.isBlocked(payload.senderId());
            HistoryMessageDisplay.display(payload, player.chatAbilities(), senderRestricted, HistoryClient.CONFIG.active().skipHistoryNarration(),
                    insert -> integrations.display(presentation, insert),
                    content -> heads.display(presentation, id -> client.getConnection().getPlayerInfo(id),
                            decorated -> client.gui.getChat().addServerSystemMessage(decorated)),
                    content -> client.getNarrator().saySystemChatQueued(content));
    }
}
