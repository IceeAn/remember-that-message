package dev.iceean.rememberthatmessage.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** 只保护本模组在当前命令树中拥有的入口。 */
public final class ClientCommandNames {
    static boolean startupEnabled;
    static CommandDispatcher<FabricClientCommandSource> dispatcher;
    static CommandNode<FabricClientCommandSource> alias;
    private ClientCommandNames() { }
    public static boolean blocks(String value) {
        if (matches(value, "rememberthatmessageclient")) return true;
        return alias != null && dispatcher != null && dispatcher.getRoot().getChild("rtmclient") == alias
                && matches(value, "rtmclient");
    }
    private static boolean matches(String value, String root) {
        return value.equals(root) || value.startsWith(root + " ");
    }
}
