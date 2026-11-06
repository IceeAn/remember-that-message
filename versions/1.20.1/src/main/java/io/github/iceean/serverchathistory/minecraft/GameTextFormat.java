package io.github.iceean.serverchathistory.minecraft;
import io.github.iceean.serverchathistory.storage.HistoryEncoding;
import io.github.iceean.serverchathistory.text.ComponentFormat;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
public final class GameTextFormat {
    // 客户端设置属于连接；重生替换玩家实例时仍应保留。
    private static final Map<ServerGamePacketListenerImpl, String> LANGUAGES = new WeakHashMap<>();
    public static String language(ServerPlayer player) { return LANGUAGES.getOrDefault(player.connection, "en_us"); }
    public static void language(ServerPlayer player, String language) {
        if (player.connection != null) LANGUAGES.put(player.connection, language);
    }
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> disconnected(listener));
        ServerLifecycleEvents.SERVER_STOPPED.register(GameTextFormat::stopped);
    }
    static void disconnected(ServerGamePacketListenerImpl listener) { LANGUAGES.remove(listener); }
    static void stopped(MinecraftServer server) {
        LANGUAGES.keySet().removeIf(listener -> listener.player.level().getServer() == server);
    }
    private GameTextFormat() { }
    public static final HistoryEncoding CURRENT = new HistoryEncoding("1.20.1", ComponentFormat.MC_1_20_1);
}
