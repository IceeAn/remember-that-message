package dev.iceean.rememberthatmessage;

import dev.iceean.rememberthatmessage.config.ConfigManager;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.minecraft.*;
import dev.iceean.rememberthatmessage.storage.*;
import dev.iceean.rememberthatmessage.text.Translations;
import dev.iceean.rememberthatmessage.network.*;
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
    private final JsonHistoryStore store;

    public WorldHistory(MinecraftServer server, Path configPath) {
        this.server = server;
        config = new ConfigManager(configPath, failure -> RememberThatMessageMod.LOGGER.error("Cannot load or save chat history configuration", failure));
        config.reload();
        var codec = new MessageCodec(server.registryAccess());
        capture = new MessageCapture(codec);
        replayCache = new ReplayMessageCache(codec);
        transport = new VanillaReplayTransport(replayCache);
        store = new JsonHistoryStore(server.getWorldPath(LevelResource.ROOT).resolve("data/remember_that_message/history.json"),
                GameTextFormat.CURRENT, codec::validate, failure -> {
                    recoveryError = failure.toString();
                    RememberThatMessageMod.LOGGER.error("Chat history recovery encountered an error", failure);
                });
        var buffer = new HistoryBuffer(store.load(), config.active().historyLimit());
        if (store.migrationReport() != null) RememberThatMessageMod.LOGGER.warn("History migration: {}", store.migrationReport());
        writer = new AsyncHistoryWriter(store, failure -> RememberThatMessageMod.LOGGER.error("Cannot persist chat history; recording continues in memory", failure));
        writer.save(buffer.snapshot());
        session = new HistorySession<>(config, buffer, writer, new ReplayPlatform<>() {
            public boolean ready(ServerPlayer player) { return PortableServerNetworking.ready(player); }
            public ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
            public ReplayDelivery delivery(ServerPlayer player, dev.iceean.rememberthatmessage.config.ModConfig config) {
                return ServerHistoryNetworking.delivery(player, config);
            }
            public boolean visible(ServerPlayer player, HistoryEntry entry) { return transport.visible(player, entry); }
            public void send(ServerPlayer player, List<HistoryEntry> entries, boolean enhanced) { transport.send(player, entries, enhanced); }
            public void boundary(ServerPlayer player, boolean start, int count) {
                if (PortableServerNetworking.active(player)) PortableServerNetworking.boundary(player, start, count);
                else GameNetworking.legacyBoundary(player, start, count);
            }
            public void notice(ServerPlayer player, String key, boolean error, Object... args) {
                player.sendSystemMessage(Component.literal(Translations.text(dev.iceean.rememberthatmessage.minecraft.GameTextFormat.language(player), key, args))
                        .withStyle(error ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
            public void failed(UUID player, RuntimeException failure) {
                RememberThatMessageMod.LOGGER.error("Cannot replay chat history to {}", player, failure);
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
    public JsonHistoryStore.MigrationReport migrationReport() { return store.migrationReport(); }
    public String migrationFailure() { return store.blockedReason(); }
    public int pendingWrites() { return writer.pendingJobs(); }
    public void record(HistoryEntry.Message message) { session.record(message); }
    public void configChanged() { session.configChanged(); }
    public CompletableFuture<Void> clear() { return session.clear(); }
    public void joined(ServerPlayer player) { session.joined(player.getUUID()); }
    public void tick() { session.tick(); }
    @Override public void close() { session.close(); }
}
