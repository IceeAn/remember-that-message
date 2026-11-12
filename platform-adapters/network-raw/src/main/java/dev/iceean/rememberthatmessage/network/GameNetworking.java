package dev.iceean.rememberthatmessage.network;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
public final class GameNetworking {
    public static final ResourceLocation CHANNEL = new ResourceLocation("remember_that_message", "bridge_v2");
    private GameNetworking() { }
    public static void register() { }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player, CHANNEL); }
    public static void send(ServerPlayer player, byte[] data) {
        var buffer = PacketByteBufs.create(); buffer.writeByteArray(data);
        ServerPlayNetworking.send(player, CHANNEL, buffer);
    }
    public static void receiveServer(BiConsumer<ServerPlayer, byte[]> receiver) {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buffer, sender) -> {
            byte[] data = buffer.readByteArray(1024);
            server.execute(() -> receiver.accept(player, data));
        });
    }
    public static void legacyBoundary(ServerPlayer player, boolean start, int count) { throw new IllegalStateException("No legacy protocol on 1.20.1"); }
    public static void legacyItem(ServerPlayer player, HistoryProtocol.Item item) { throw new IllegalStateException("No legacy protocol on 1.20.1"); }
}
