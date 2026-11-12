package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.RememberThatMessageMod;
import dev.iceean.rememberthatmessage.config.ModConfig;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.minecraft.*;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class PortableServerNetworking {
    // 重生会替换玩家实体，但保留 Play 连接监听器；状态不能依附旧实体的弱引用。
    private static final Map<ServerGamePacketListenerImpl, PlaySession> SESSIONS = new WeakHashMap<>();
    private static final Set<ServerGamePacketListenerImpl> WARNED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerGamePacketListenerImpl> FAILED = Collections.newSetFromMap(new WeakHashMap<>());
    private PortableServerNetworking() { }
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> joined(listener));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> disconnected(listener));
        ServerLifecycleEvents.SERVER_STOPPED.register(PortableServerNetworking::stopped);
        GameNetworking.receiveServer((player, data) -> {
            var state = session(player);
            if (state == null || state.ready(System.nanoTime())) return;
            try {
                if (data.length > 1024) return;
                if (PortableProtocol.decode(data) instanceof PortableProtocol.Reply reply) state.reply(reply);
            } catch (RuntimeException failure) { /* 无效回复等待超时，不能影响玩家连接。 */ }
        });
    }
    static PlaySession joined(ServerGamePacketListenerImpl listener) {
        var state = new PlaySession(System.nanoTime());
        SESSIONS.put(listener, state);
        WARNED.remove(listener);
        FAILED.remove(listener);
        return state;
    }
    static void disconnected(ServerGamePacketListenerImpl listener) {
        SESSIONS.remove(listener);
        WARNED.remove(listener);
        FAILED.remove(listener);
    }
    static void stopped(MinecraftServer server) {
        SESSIONS.keySet().removeIf(listener -> listener.player.level().getServer() == server);
        WARNED.removeIf(listener -> listener.player.level().getServer() == server);
        FAILED.removeIf(listener -> listener.player.level().getServer() == server);
    }
    static PlaySession session(ServerPlayer player) { return SESSIONS.get(player.connection); }
    public static boolean ready(ServerPlayer player) {
        var state = session(player);
        if (state == null) return true;
        if (GameNetworking.canSend(player) && state.offer())
            send(player, new PortableProtocol.Hello(state.id(), PortableProtocol.VERSION, GameTextFormat.CURRENT.componentEncoding()));
        return state.ready(System.nanoTime());
    }
    public static ReplayDelivery delivery(ServerPlayer player, ModConfig config) {
        var state = session(player);
        if (state == null || !state.offered()) return null;
        if (!state.accepted()) {
            if (GameNetworking.canSend(player)) send(player, new PortableProtocol.Status(state.id(), false, ReplayPreferences.DEFAULT, state.reason()));
            return new ReplayDelivery(ReplayPreferences.DEFAULT, false, true, state.reason());
        }
        boolean enhanced = GameNetworking.canSend(player) && config.replayMode() == ModConfig.ReplayMode.AUTO;
        String reason = enhanced ? "enhanced" : "forced_system";
        send(player, new PortableProtocol.Status(state.id(), enhanced, state.preferences(), reason));
        return new ReplayDelivery(state.preferences(), enhanced, false, reason);
    }
    public static boolean active(ServerPlayer player) { var state = session(player); return state != null && state.accepted(); }
    public static void boundary(ServerPlayer player, boolean start, int count) {
        var state = session(player);
        send(player, new PortableProtocol.Boundary(state.id(), start, count));
    }
    public static void item(ServerPlayer player, HistoryEntry entry, boolean filtered, ReplayMessageCache cache) {
        var state = session(player);
        ComponentFormat format = state.format() == GameTextFormat.CURRENT.componentEncoding() ? state.format() : ComponentFormat.PORTABLE_V1;
        var body = cache.encode(state.id(), entry, filtered, format);
        if (body.failure() != null && FAILED.add(player.connection))
            RememberThatMessageMod.LOGGER.warn("History record {} replaced with a placeholder for {}: {}",
                    entry.sequence(), player.getUUID(), body.failure().getClass().getSimpleName());
        if (!body.losses().isEmpty() && WARNED.add(player.connection))
            RememberThatMessageMod.LOGGER.warn("Cross-version history presentation degraded for {}: {}", player.getUUID(), body.losses());
        // 网络异常不能降级为占位后重发，发送可能已经部分完成。
        GameNetworking.send(player, body.bytes());
    }
    public static void send(ServerPlayer player, PortableProtocol.Frame frame) { GameNetworking.send(player, PortableProtocol.encode(frame)); }
}
