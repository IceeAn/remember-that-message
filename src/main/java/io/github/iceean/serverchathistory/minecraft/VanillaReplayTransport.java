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
    private final MessageCodec codec;
    public VanillaReplayTransport(MinecraftServer server, MessageCodec codec) { this.server = server; this.codec = codec; }

    public static boolean needsFiltering(HistoryEntry.Chat chat, UUID recipient, boolean recipientFiltering) {
        return !chat.senderId().equals(recipient) && (chat.senderFiltering() || recipientFiltering);
    }

    private FilterMask mask(ServerPlayer player, HistoryEntry.Chat chat) {
        return needsFiltering(chat, player.getUUID(), player.isTextFilteringEnabled())
                ? MessageCodec.decodeMask(chat.filterMaskJson()) : FilterMask.PASS_THROUGH;
    }

    @Override public List<HistoryEntry> visibleTo(ServerPlayer recipient, List<HistoryEntry> entries) {
        var visibility = recipient.getChatVisibility();
        if (visibility == ChatVisiblity.HIDDEN) return List.of();
        // Vanilla receive checks use chat visibility, not command-level permissions.
        return entries.stream().filter(entry -> entry.message().chat() == null
                || (visibility == ChatVisiblity.FULL
                    && !mask(recipient, entry.message().chat()).isFullyFiltered())).toList();
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
                if (chat == null) { recipient.sendSystemMessage(codec.decode(message.renderedJson())); continue; }
                var mask = mask(recipient, chat);
                var bound = ChatType.bind(ChatType.CHAT, server.registryAccess(), codec.decode(chat.nameJson()));
                if (playerPackets) {
                    // Maintains the connection's real packet index and signature cache.
                    recipient.connection.sendPlayerChatMessage(unsigned(message, codec.decode(chat.bodyJson()), mask), bound);
                } else {
                    var rendered = mask.isEmpty() ? codec.decode(message.renderedJson()) : bound.decorate(mask.applyWithFormatting(chat.text()));
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
