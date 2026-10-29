package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.ConfigManager;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.storage.*;
import io.github.iceean.serverchathistory.text.Translations;
import io.github.iceean.serverchathistory.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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
    private final HistoryBuffer buffer;
    private final AsyncHistoryWriter writer;
    private final VanillaReplayTransport transport;
    private final MessageCapture capture;
    private final ReplayMessageCache replayCache;
    private final LinkedHashSet<UUID> pendingJoins = new LinkedHashSet<>();
    private boolean replaying;
    private boolean dirty;
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
        buffer = new HistoryBuffer(store.load(), config.active().historyLimit());
        writer = new AsyncHistoryWriter(store, failure -> ServerChatHistoryMod.LOGGER.error("Cannot persist chat history; recording continues in memory", failure));
        writer.save(buffer.snapshot());
    }

    public ConfigManager config() { return config; }
    public MessageCapture capture() { return capture; }
    public int size() { return buffer.size(); }
    public int visibleSize() { return ReplayPolicy.RECENT.select(buffer.snapshot(), config.active()).size(); }

    public boolean storageError() { return recoveryError != null || writer.lastFailure() != null; }
    public int pendingWrites() { return writer.pendingJobs(); }

    public void record(HistoryEntry.Message message) {
        if (replaying || message == null || !config.active().includes(message.kind())) return;
        buffer.append(message);
        dirty = true;
    }

    public void configChanged() {
        buffer.resize(config.active().historyLimit());
        saveSnapshot();
    }

    private void saveSnapshot() {
        replayCache.retain(buffer.snapshot());
        writer.save(buffer.snapshot());
        dirty = false;
    }

    public CompletableFuture<Void> clear() {
        buffer.clear();
        replayCache.clear();
        dirty = false;
        return writer.clear(buffer.snapshot());
    }

    public void joined(ServerPlayer player) { pendingJoins.add(player.getUUID()); }

    public void tick() {
        if (dirty) saveSnapshot();
        // JOIN fires partway through placeNewPlayer. End-of-tick runs after its join
        // announcement and player-info packets, without introducing a timer delay.
        var joined = pendingJoins.toArray(UUID[]::new);
        pendingJoins.clear();
        var entries = joined.length == 0 ? List.<HistoryEntry>of() : ReplayPolicy.RECENT.select(buffer.snapshot(), config.active());
        for (var id : joined) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            try { replay(player, entries); }
            catch (RuntimeException failure) { ServerChatHistoryMod.LOGGER.error("Cannot replay chat history to {}", id, failure); }
        }
    }

    private void replay(ServerPlayer player, List<HistoryEntry> history) {
        var delivery = ServerHistoryNetworking.delivery(player, config.active());
        if (delivery.failed()) notice(player, "negotiation_failed", true);
        if (!delivery.preferences().receiveHistory()) return;
        var entries = ReplaySelection.latest(history, delivery.preferences(), entry -> transport.visible(player, entry));
        if (delivery.enhanced()) ServerPlayNetworking.send(player, new HistoryProtocol.Boundary(true, entries.size()));
        replaying = true;
        try {
            if (!entries.isEmpty()) {
                notice(player, "start", false, entries.size());
                transport.send(player, entries, delivery.enhanced());
            }
            for (var notice : NoticePolicy.after(entries.size(), config.active(), config.hasError())) {
                notice(player, notice.name().toLowerCase(java.util.Locale.ROOT), notice == NoticePolicy.Notice.CONFIG_ERROR);
            }
        } finally {
            replaying = false;
            if (delivery.enhanced()) ServerPlayNetworking.send(player, new HistoryProtocol.Boundary(false, entries.size()));
        }
    }

    private static void notice(ServerPlayer player, String key, boolean error, Object... args) {
        player.sendSystemMessage(Component.literal(Translations.text(player.clientInformation().language(), key, args))
                .withStyle(error ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    @Override public void close() {
        pendingJoins.clear();
        try {
            writer.flush(buffer.snapshot());
            writer.close();
        } finally { replayCache.clear(); }
    }
}
