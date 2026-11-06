package io.github.iceean.serverchathistory.client;
import io.github.iceean.serverchathistory.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public final class GameClientNetworking {
    private GameClientNetworking() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GameNetworking.Bridge.TYPE, (payload, context) -> HistoryClient.receivePortable(payload.bytes()));
    }
    public static void send(byte[] data) { ClientPlayNetworking.send(new GameNetworking.Bridge(data)); }
}
