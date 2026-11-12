package dev.iceean.rememberthatmessage.minecraft;

import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.network.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

/** Selects recipient-safe content before either vanilla or enhanced delivery. */
public final class VanillaReplayTransport implements ReplayTransport {
    private final ReplayMessageCache cache;
    public VanillaReplayTransport(ReplayMessageCache cache) { this.cache = cache; }
    public static boolean needsFiltering(HistoryEntry.Chat chat, UUID recipient, boolean recipientFiltering) {
        return !chat.senderId().equals(recipient) && (chat.senderFiltering() || recipientFiltering);
    }
    public boolean visible(ServerPlayer recipient, HistoryEntry entry) {
        var visibility = recipient.getChatVisibility();
        if (visibility == ChatVisiblity.HIDDEN) return false;
        var chat = entry.message().chat();
        return chat == null || (visibility == ChatVisiblity.FULL && !(chat.filtering() == HistoryEntry.Filtering.FULL
                && needsFiltering(chat, recipient.getUUID(), recipient.isTextFilteringEnabled())));
    }
    @Override public List<HistoryEntry> visibleTo(ServerPlayer recipient, List<HistoryEntry> entries) {
        return entries.stream().filter(entry -> visible(recipient, entry)).toList();
    }
    public Component content(ServerPlayer recipient, HistoryEntry entry) {
        var chat = entry.message().chat();
        if (chat != null && needsFiltering(chat, recipient.getUUID(), recipient.isTextFilteringEnabled())) {
            if (chat.filtering() == HistoryEntry.Filtering.FULL) throw new IllegalArgumentException("Fully filtered history");
            if (chat.filtering() == HistoryEntry.Filtering.PARTIAL) return cache.get(entry).filtered();
        }
        return cache.get(entry).rendered();
    }
    @Override public void send(ServerPlayer recipient, List<HistoryEntry> entries, boolean enhanced) {
        for (var entry : entries) {
            if (!visible(recipient, entry)) continue;
            var message = entry.message(); var chat = message.chat();
            if (enhanced && PortableServerNetworking.active(recipient)) {
                boolean filtered = chat != null && chat.filtering() == HistoryEntry.Filtering.PARTIAL
                        && needsFiltering(chat, recipient.getUUID(), recipient.isTextFilteringEnabled());
                PortableServerNetworking.item(recipient, entry, filtered, cache);
                continue;
            }
            var content = content(recipient, entry);
            if (enhanced) GameNetworking.legacyItem(recipient, new HistoryProtocol.Item(entry.sequence(), message.kind(),
                    message.time(), content, chat == null ? null : chat.senderId(), chat == null ? null : chat.senderName()));
            else recipient.sendSystemMessage(content);
        }
    }
}
