package dev.iceean.rememberthatmessage.minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public final class GameProfiles {
    private GameProfiles() { }
    public static String name(ServerPlayer player) { return player.getGameProfile().name(); }
    public static boolean owner(MinecraftServer server, ServerPlayer player) {
        return server.isSingleplayerOwner(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
    }
}
