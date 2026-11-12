package dev.iceean.rememberthatmessage.client;
import net.minecraft.network.chat.Component;
public final class ClientText {
    private ClientText() { }
    public static Component text(String key, Object... args) { return Component.translatable("remember_that_message.client." + key, args); }
}
