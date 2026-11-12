package dev.iceean.rememberthatmessage.client;
import dev.iceean.rememberthatmessage.network.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
public final class GameClientNetworking {
    private GameClientNetworking() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GameNetworking.CHANNEL, (client, handler, buffer, sender) -> {
            byte[] data = buffer.readByteArray(PortableProtocol.MAX_FRAME);
            client.execute(() -> HistoryClient.receivePortable(data));
        });
    }
    public static void send(byte[] data) {
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); buffer.writeByteArray(data);
        ClientPlayNetworking.send(GameNetworking.CHANNEL, buffer);
    }
    public static void registerLegacy() {
    }
}
