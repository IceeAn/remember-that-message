package dev.iceean.rememberthatmessage.minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public final class GameProfiles {
    private GameProfiles() { }
    public static String name(ServerPlayer player) { return player.getGameProfile().getName(); }
    public static boolean owner(MinecraftServer server, ServerPlayer player) {
        return server.isSingleplayerOwner(player.getGameProfile());
    }
}
