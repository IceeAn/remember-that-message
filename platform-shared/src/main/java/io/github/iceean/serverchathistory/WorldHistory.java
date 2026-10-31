package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.ConfigManager;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.storage.*;
import io.github.iceean.serverchathistory.text.Translations;
import io.github.iceean.serverchathistory.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** One instance per logical server; all access except the writer is server-thread confined. */
public final class WorldHistory implements AutoCloseable {
    private final MinecraftServer server;
    private final ConfigManager config;
    private final AsyncHistoryWriter writer;
    private final VanillaReplayTransport transport;
    private final MessageCapture capture;
    private final ReplayMessageCache replayCache;
    private final HistorySession<ServerPlayer> session;
    private String recoveryError;

    public WorldHistory(MinecraftServer server, Path configPath) {
        this.server = server;
        config = new ConfigManager(configPath, failure -> ServerChatHistoryMod.LOGGER.error("Cannot load or save chat history configuration", failure));
        config.reload();
        var codec = new MessageCodec(server.registryAccess());
        capture = new MessageCapture(codec);
        replayCache = new ReplayMessageCache(codec);
        transport = new VanillaReplayTransport(replayCache);
        var store = new JsonHistoryStore(server.getWorldPath(LevelResource.ROOT).resolve("data/server_chat_history/history.json"),
                codec::validate, failure -> {
                    recoveryError = failure.toString();
                    ServerChatHistoryMod.LOGGER.error("Chat history recovery encountered an error", failure);
                });
        var buffer = new HistoryBuffer(store.load(), config.active().historyLimit());
        writer = new AsyncHistoryWriter(store, failure -> ServerChatHistoryMod.LOGGER.error("Cannot persist chat history; recording continues in memory", failure));
        writer.save(buffer.snapshot());
        session = new HistorySession<>(config, buffer, writer, new ReplayPlatform<>() {
            public ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
            public ReplayDelivery delivery(ServerPlayer player, io.github.iceean.serverchathistory.config.ModConfig config) {
                return ServerHistoryNetworking.delivery(player, config);
            }
            public boolean visible(ServerPlayer player, HistoryEntry entry) { return transport.visible(player, entry); }
            public void send(ServerPlayer player, List<HistoryEntry> entries, boolean enhanced) { transport.send(player, entries, enhanced); }
            public void boundary(ServerPlayer player, boolean start, int count) {
                ServerPlayNetworking.send(player, new HistoryProtocol.Boundary(start, count));
            }
            public void notice(ServerPlayer player, String key, boolean error, Object... args) {
                player.sendSystemMessage(Component.literal(Translations.text(player.clientInformation().language(), key, args))
                        .withStyle(error ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
            public void failed(UUID player, RuntimeException failure) {
                ServerChatHistoryMod.LOGGER.error("Cannot replay chat history to {}", player, failure);
            }
            public void retainDecoded(HistorySnapshot snapshot) { replayCache.retain(snapshot); }
            public void clearDecoded() { replayCache.clear(); }
        });
    }

    public ConfigManager config() { return config; }
    public MessageCapture capture() { return capture; }
    public int size() { return session.size(); }
    public int visibleSize() { return session.visibleSize(); }
    public boolean storageError() { return recoveryError != null || writer.lastFailure() != null; }
    public int pendingWrites() { return writer.pendingJobs(); }
    public void record(HistoryEntry.Message message) { session.record(message); }
    public void configChanged() { session.configChanged(); }
    public CompletableFuture<Void> clear() { return session.clear(); }
    public void joined(ServerPlayer player) { session.joined(player.getUUID()); }
    public void tick() { session.tick(); }
    @Override public void close() { session.close(); }
}
