package dev.iceean.rememberthatmessage.client.compat;

import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.PlayerInfo;

/** 对应经过验证的 Chat Heads 消息入口，不扫描候选签名。 */
final class GameChatHeads {
    static final String DESCRIPTOR = "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;";
    static Method method(Class<?> heads) throws ReflectiveOperationException {
        return heads.getMethod("handleAddedMessage", Component.class, PlayerInfo.class);
    }
    static Component decorate(Method method, Component content, PlayerInfo player) throws ReflectiveOperationException {
        return (Component) method.invoke(null, content, player);
    }
}
