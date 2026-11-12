package dev.iceean.rememberthatmessage.client;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
public final class ClientCommandGuard {
    private ClientCommandGuard() { }
    public static boolean blocks(Style style) {
        return style != null && style.getClickEvent() instanceof ClickEvent.RunCommand command && blocksCommand(command.command());
    }
    public static boolean blocksCommand(String command) {
        // 与新版原版一致：命令允许不带开头斜杠，不再对正文做旧式字符过滤。
        String value = command.startsWith("/") ? command.substring(1) : command;
        return ClientCommandNames.blocks(value);
    }
}
