package io.github.iceean.serverchathistory.network;
import io.github.iceean.serverchathistory.config.ModConfig;
import net.minecraft.server.level.ServerPlayer;
public final class ServerHistoryNetworking {
    private ServerHistoryNetworking() { }
    public static void register() {
        io.github.iceean.serverchathistory.minecraft.GameTextFormat.register();
        PortableServerNetworking.register();
    }
    public static ReplayDelivery delivery(ServerPlayer player, ModConfig config) {
        var result = PortableServerNetworking.delivery(player, config);
        return result != null ? result : ReplayDelivery.resolve(null, false, config.replayMode());
    }
}
