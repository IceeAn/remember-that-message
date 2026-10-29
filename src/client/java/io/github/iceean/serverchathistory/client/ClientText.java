package io.github.iceean.serverchathistory.client;
import net.minecraft.network.chat.Component;
public final class ClientText {
    private ClientText() { }
    public static Component text(String key, Object... args) { return Component.translatable("server_chat_history.client." + key, args); }
}
