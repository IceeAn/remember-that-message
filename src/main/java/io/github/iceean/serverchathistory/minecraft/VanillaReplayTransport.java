package io.github.iceean.serverchathistory.minecraft;

import io.github.iceean.serverchathistory.core.HistoryEntry;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

/** Uses only vanilla packets. All changes to player info are recipient-local. */
public final class VanillaReplayTransport implements ReplayTransport {
    private final MinecraftServer server;
    private final ReplayMessageCache cache;
    public VanillaReplayTransport(MinecraftServer server, ReplayMessageCache cache) { this.server = server; this.cache = cache; }

    public static boolean needsFiltering(HistoryEntry.Chat chat, UUID recipient, boolean recipientFiltering) {
        return !chat.senderId().equals(recipient) && (chat.senderFiltering() || recipientFiltering);
    }

    private FilterMask mask(ServerPlayer player, HistoryEntry entry) {
        var chat = entry.message().chat();
        return needsFiltering(chat, player.getUUID(), player.isTextFilteringEnabled())
                ? cache.get(entry).mask() : FilterMask.PASS_THROUGH;
    }

    @Override public List<HistoryEntry> visibleTo(ServerPlayer recipient, List<HistoryEntry> entries) {
        var visibility = recipient.getChatVisibility();
        if (visibility == ChatVisiblity.HIDDEN) return List.of();
        // Vanilla receive checks use chat visibility, not command-level permissions.
        return entries.stream().filter(entry -> entry.message().chat() == null
                || (visibility == ChatVisiblity.FULL
                    && !mask(recipient, entry).isFullyFiltered())).toList();
    }

    public static PlayerChatMessage unsigned(HistoryEntry.Message message, Component body, FilterMask mask) {
        return new PlayerChatMessage(SignedMessageLink.unsigned(message.chat().senderId()), null,
                new SignedMessageBody(message.chat().text(), message.time(), 0, LastSeenMessages.EMPTY), body, mask);
    }

    @Override public void send(ServerPlayer recipient, List<HistoryEntry> entries, boolean playerPackets) {
        Runnable replay = () -> {
            for (var entry : entries) {
                var message = entry.message();
                var chat = message.chat();
                var decoded = cache.get(entry);
                if (chat == null) { recipient.sendSystemMessage(decoded.rendered()); continue; }
                var mask = mask(recipient, entry);
                if (playerPackets) {
                    var bound = ChatType.bind(ChatType.CHAT, server.registryAccess(), decoded.name());
                    // Maintains the connection's real packet index and signature cache.
                    recipient.connection.sendPlayerChatMessage(unsigned(message, decoded.body(), mask), bound);
                } else {
                    var rendered = mask.isEmpty() ? decoded.rendered()
                            : ChatType.bind(ChatType.CHAT, server.registryAccess(), decoded.name()).decorate(mask.applyWithFormatting(chat.text()));
                    recipient.sendSystemMessage(rendered);
                }
            }
        };
        if (!playerPackets) { replay.run(); return; }
        var lease = new PlayerInfoLease(id -> {
            var player = server.getPlayerList().getPlayer(id);
            return player == null ? null : new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT, player).entries().getFirst();
        }, recipient.connection::send, PlayerInfoLease::packet);
        lease.run(entries.stream().map(entry -> entry.message().chat()).filter(java.util.Objects::nonNull).toList(), replay);
    }
}
