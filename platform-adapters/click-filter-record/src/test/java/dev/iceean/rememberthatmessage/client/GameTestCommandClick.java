package dev.iceean.rememberthatmessage.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

/** 对应原版点击处理的字符过滤和命令前缀规则。 */
final class GameTestCommandClick {
    private GameTestCommandClick() { }
    static Style style(String command) { return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)); }
    static String prepare(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
