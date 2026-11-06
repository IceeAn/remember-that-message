package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.config.ModConfig;
import io.github.iceean.serverchathistory.core.ReplayPreferences;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;

public final class ServerHistoryNetworking {
    private static final Map<Connection, Negotiation> CONNECTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private ServerHistoryNetworking() { }
    public static void register() {
        PortableServerNetworking.register();
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            var connection = listener.getPacketContext().orElseThrow(PacketContext.CONNECTION);
            CONNECTIONS.remove(connection);
            if (!ServerConfigurationNetworking.canSend(listener, HistoryProtocol.Hello.TYPE)) return;
            var state = new Negotiation(); CONNECTIONS.put(connection, state);
            listener.addTask(new ConfigurationTask() {
                public void start(Consumer<Packet<?>> sender) {
                    state.start(System.nanoTime());
                    sender.accept(ServerConfigurationNetworking.createClientboundPacket(new HistoryProtocol.Hello(HistoryProtocol.VERSION)));
                }
                public boolean tick() { return state.tick(System.nanoTime()); }
                public Type type() { return new Type("server_chat_history:preferences"); }
            });
        });
        ServerConfigurationNetworking.registerGlobalReceiver(HistoryProtocol.Preferences.TYPE, (payload, context) -> {
            var state = CONNECTIONS.get(context.packetContext().orElseThrow(PacketContext.CONNECTION));
            if (state != null) state.reply(payload.version(), payload.value());
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) ->
                CONNECTIONS.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                CONNECTIONS.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
    }
    public static ReplayDelivery delivery(ServerPlayer player, ModConfig config) {
        var portable = PortableServerNetworking.delivery(player, config);
        if (portable != null) return portable;
        if (player.connection == null) return resolve(null, false, config.replayMode());
        var state = CONNECTIONS.get(player.connection.getPacketContext().orElseThrow(PacketContext.CONNECTION));
        if (state == null) return resolve(null, false, config.replayMode());
        boolean channels = ServerPlayNetworking.canSend(player, HistoryProtocol.Status.TYPE)
                && ServerPlayNetworking.canSend(player, HistoryProtocol.Item.TYPE)
                && ServerPlayNetworking.canSend(player, HistoryProtocol.Boundary.TYPE);
        var result = resolve(state, channels, config.replayMode());
        if (ServerPlayNetworking.canSend(player, HistoryProtocol.Status.TYPE))
            ServerPlayNetworking.send(player, new HistoryProtocol.Status(!result.failed(), result.enhanced(), result.preferences(), result.reason()));
        return result;
    }
    static ReplayDelivery resolve(Negotiation state, boolean channels, ModConfig.ReplayMode mode) {
        return ReplayDelivery.resolve(state, channels, mode);
    }
}
