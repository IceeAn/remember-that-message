package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.config.ModConfig;
import io.github.iceean.serverchathistory.core.ReplayPreferences;
import io.github.iceean.serverchathistory.mixin.CommonListenerAccessor;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public final class ServerHistoryNetworking {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("server_chat_history:preferences");
    private static final Map<Connection, Negotiation> CONNECTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerConfigurationPacketListenerImpl, Pending> PENDING = new ConcurrentHashMap<>();
    private record Pending(MinecraftServer server, Connection connection, Negotiation state) { }
    private ServerHistoryNetworking() { }

    private static Connection connection(ServerCommonPacketListenerImpl listener) {
        return ((CommonListenerAccessor) listener).serverChatHistory$connection();
    }
    public static void register() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            var connection = connection(listener);
            CONNECTIONS.remove(connection);
            PENDING.remove(listener);
            if (!ServerConfigurationNetworking.canSend(listener, HistoryProtocol.Hello.TYPE)) return;
            listener.addTask(queue(listener, server, connection));
        });
        ServerConfigurationNetworking.registerGlobalReceiver(HistoryProtocol.Preferences.TYPE, (payload, context) -> {
            reply(connection(context.networkHandler()), payload);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, System.nanoTime()));
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> disconnect(listener, connection(listener)));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> CONNECTIONS.remove(connection(listener)));
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerHistoryNetworking::stop);
    }
    static ConfigurationTask queue(ServerConfigurationPacketListenerImpl listener, MinecraftServer server, Connection connection) {
        var state = new Negotiation();
        CONNECTIONS.put(connection, state);
        PENDING.put(listener, new Pending(server, connection, state));
        return new ConfigurationTask() {
            public void start(Consumer<Packet<?>> sender) {
                state.start(System.nanoTime());
                sender.accept(ServerConfigurationNetworking.createS2CPacket(new HistoryProtocol.Hello(HistoryProtocol.VERSION)));
            }
            public Type type() { return TASK; }
        };
    }
    static void reply(Connection connection, HistoryProtocol.Preferences payload) {
        var state = CONNECTIONS.get(connection);
        if (state != null) state.reply(payload.version(), payload.value());
    }
    // 1.21.1 configuration tasks have no tick hook. Complete only our started task,
    // on the owning server thread, after either a reply or the shared timeout.
    static void tick(MinecraftServer server, long now) {
        PENDING.forEach((listener, pending) -> {
            if (pending.server() == server && pending.state().tick(now)
                    && PENDING.remove(listener, pending) && pending.connection().isConnected()) {
                listener.completeTask(TASK);
            }
        });
    }
    static void disconnect(ServerConfigurationPacketListenerImpl listener, Connection connection) {
        PENDING.remove(listener);
        CONNECTIONS.remove(connection);
    }
    static void stop(MinecraftServer server) {
        PENDING.forEach((listener, pending) -> {
            if (pending.server() == server && PENDING.remove(listener, pending)) CONNECTIONS.remove(pending.connection());
        });
    }

    public record Delivery(ReplayPreferences preferences, boolean enhanced, boolean failed, String reason) { }
    public static Delivery delivery(ServerPlayer player, ModConfig config) {
        if (player.connection == null) return resolve(null, false, config.replayMode());
        var state = CONNECTIONS.get(connection(player.connection));
        if (state == null) return resolve(null, false, config.replayMode());
        boolean channels = ServerPlayNetworking.canSend(player, HistoryProtocol.Status.TYPE)
                && ServerPlayNetworking.canSend(player, HistoryProtocol.Item.TYPE)
                && ServerPlayNetworking.canSend(player, HistoryProtocol.Boundary.TYPE);
        var result = resolve(state, channels, config.replayMode());
        if (ServerPlayNetworking.canSend(player, HistoryProtocol.Status.TYPE))
            ServerPlayNetworking.send(player, new HistoryProtocol.Status(!result.failed(), result.enhanced(), result.preferences(), result.reason()));
        return result;
    }
    static Delivery resolve(Negotiation state, boolean channels, ModConfig.ReplayMode mode) {
        if (state == null) return new Delivery(ReplayPreferences.DEFAULT, false, false, "vanilla");
        boolean ready = state.accepted() && channels;
        boolean enhanced = ready && mode == ModConfig.ReplayMode.AUTO;
        String reason = ready ? enhanced ? "enhanced" : "forced_system" : state.accepted() ? "missing_channel" : state.reason();
        var prefs = ready ? state.preferences() : ReplayPreferences.DEFAULT;
        return new Delivery(prefs, enhanced, !ready, reason);
    }
}
