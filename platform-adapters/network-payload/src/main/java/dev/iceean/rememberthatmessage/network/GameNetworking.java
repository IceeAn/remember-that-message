package dev.iceean.rememberthatmessage.network;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class GameNetworking {
    private GameNetworking() { }
    public record Bridge(byte[] bytes) implements CustomPacketPayload {
        public static final Type<Bridge> TYPE = new Type<>(GameIdentifiers.id("bridge_v2"));
        public static final StreamCodec<FriendlyByteBuf, Bridge> CODEC = CustomPacketPayload.codec(
                (value, buffer) -> buffer.writeByteArray(value.bytes()), buffer -> new Bridge(buffer.readByteArray(PortableProtocol.MAX_FRAME)));
        public Type<Bridge> type() { return TYPE; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Bridge.TYPE, Bridge.CODEC);
        PayloadTypeRegistry.playC2S().register(Bridge.TYPE, Bridge.CODEC);
    }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player, Bridge.TYPE); }
    public static void send(ServerPlayer player, byte[] bytes) { ServerPlayNetworking.send(player, new Bridge(bytes)); }
    public static void receiveServer(BiConsumer<ServerPlayer, byte[]> receiver) {
        ServerPlayNetworking.registerGlobalReceiver(Bridge.TYPE, (payload, context) -> receiver.accept(context.player(), payload.bytes()));
    }
    public static void legacyBoundary(ServerPlayer player, boolean start, int count) {
        ServerPlayNetworking.send(player, new HistoryProtocol.Boundary(start, count));
    }
    public static void legacyItem(ServerPlayer player, HistoryProtocol.Item item) { ServerPlayNetworking.send(player, item); }
}
