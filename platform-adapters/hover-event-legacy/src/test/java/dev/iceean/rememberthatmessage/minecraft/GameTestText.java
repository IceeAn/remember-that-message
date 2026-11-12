package dev.iceean.rememberthatmessage.minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
public final class GameTestText {
    public static HoverEvent hover(Component content) { return new HoverEvent(HoverEvent.Action.SHOW_TEXT, content); }
}
