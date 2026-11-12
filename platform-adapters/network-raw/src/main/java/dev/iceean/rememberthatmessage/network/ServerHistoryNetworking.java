package dev.iceean.rememberthatmessage.network;
import dev.iceean.rememberthatmessage.config.ModConfig;
import net.minecraft.server.level.ServerPlayer;
public final class ServerHistoryNetworking {
    private ServerHistoryNetworking() { }
    public static void register() {
        dev.iceean.rememberthatmessage.minecraft.GameTextFormat.register();
        PortableServerNetworking.register();
    }
    public static ReplayDelivery delivery(ServerPlayer player, ModConfig config) {
        var result = PortableServerNetworking.delivery(player, config);
        return result != null ? result : ReplayDelivery.resolve(null, false, config.replayMode());
    }
}
