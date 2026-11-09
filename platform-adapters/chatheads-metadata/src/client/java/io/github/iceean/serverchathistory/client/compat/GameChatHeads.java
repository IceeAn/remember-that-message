package io.github.iceean.serverchathistory.client.compat;

import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.PlayerInfo;

/** 对应经过验证的 Chat Heads 消息入口，不扫描候选签名。 */
final class GameChatHeads {
    static final String DESCRIPTOR = "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/client/multiplayer/PlayerInfo;)V";
    static Method method(Class<?> heads) throws ReflectiveOperationException {
        return heads.getMethod("handleAddedMessage", Component.class, net.minecraft.network.chat.ChatType.Bound.class, PlayerInfo.class);
    }
    static Component decorate(Method method, Component content, PlayerInfo player) throws ReflectiveOperationException {
        method.invoke(null, content, null, player); return content;
    }
}
