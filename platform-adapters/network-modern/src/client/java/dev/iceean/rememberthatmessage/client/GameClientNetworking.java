package dev.iceean.rememberthatmessage.client;
import dev.iceean.rememberthatmessage.network.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
public final class GameClientNetworking {
    private GameClientNetworking() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GameNetworking.Bridge.TYPE, (payload, context) -> HistoryClient.receivePortable(payload.bytes()));
    }
    public static void send(byte[] data) { ClientPlayNetworking.send(new GameNetworking.Bridge(data)); }
    public static void registerLegacy() {
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> HistoryClient.reset());
        ClientConfigurationNetworking.registerGlobalReceiver(HistoryProtocol.Hello.TYPE, (payload, context) -> {
            if (ClientConfigurationNetworking.canSend(HistoryProtocol.Preferences.TYPE))
                context.responseSender().sendPacket(new HistoryProtocol.Preferences(HistoryProtocol.VERSION, HistoryClient.CONFIG.active().preferences()));
        });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Status.TYPE, (payload, context) -> { if (!HistoryClient.portableActive()) HistoryClient.acceptStatus(payload); });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Boundary.TYPE, (payload, context) -> { if (!HistoryClient.portableActive()) HistoryClient.boundary(payload.start(), payload.count()); });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Item.TYPE, (payload, context) -> { if (!HistoryClient.portableActive()) HistoryClient.acceptItem(payload); });
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> HistoryClient.reset());
    }
}
