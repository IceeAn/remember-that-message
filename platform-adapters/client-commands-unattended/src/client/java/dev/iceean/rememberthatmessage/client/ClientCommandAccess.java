package dev.iceean.rememberthatmessage.client;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** 适配命令来源限制；不支持 attended 的版本另由点击 Mixin 拦截。 */
final class ClientCommandAccess {
    private ClientCommandAccess() { }
    static boolean allowed(FabricClientCommandSource source) { return source.getPlayer() != null; }
}
