package dev.iceean.rememberthatmessage.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

/** 对应原版点击处理的字符过滤和命令前缀规则。 */
final class GameTestCommandClick {
    private GameTestCommandClick() { }
    static Style style(String command) { return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)); }
    static String prepare(String command) {
        String filtered = net.minecraft.SharedConstants.filterText(command);
        return filtered.startsWith("/") ? filtered.substring(1) : null;
    }
}
