package io.github.iceean.serverchathistory.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;

/** Older Fabric has no attended command-source flag. Do not let clickable text change local settings. */
public final class ClientCommandGuard {
    private ClientCommandGuard() { }
    public static boolean blocks(Style style) {
        if (style == null) return false;
        var click = style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) return false;
        String command = StringUtil.filterText(click.getValue()).stripLeading();
        return command.equals("/chathistoryclient") || command.startsWith("/chathistoryclient ");
    }
}
