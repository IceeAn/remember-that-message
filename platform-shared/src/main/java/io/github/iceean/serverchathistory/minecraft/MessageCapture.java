package io.github.iceean.serverchathistory.minecraft;

import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.MessageKind;
import java.time.Instant;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

/** Only call from Fabric's post-allow public broadcast events. */
public final class MessageCapture {
    private final MessageCodec codec;
    public MessageCapture(MessageCodec codec) { this.codec = codec; }

    public HistoryEntry.Message chat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
        if (!codec.isPublicChat(bound)) return null;
        var mask = message.filterMask();
        var filtering = mask.isEmpty() ? HistoryEntry.Filtering.NONE
                : mask.isFullyFiltered() ? HistoryEntry.Filtering.FULL : HistoryEntry.Filtering.PARTIAL;
        String filtered = filtering == HistoryEntry.Filtering.PARTIAL
                ? codec.encode(bound.decorate(mask.applyWithFormatting(message.signedContent()))) : null;
        var data = new HistoryEntry.Chat(sender.getUUID(), GameProfiles.name(sender),
                sender.isTextFilteringEnabled(), filtering, filtered);
        return new HistoryEntry.Message(MessageKind.CHAT, Instant.now(),
                codec.encode(bound.decorate(message.decoratedContent())), data);
    }

    public HistoryEntry.Message game(Component message, boolean overlay) {
        MessageKind kind = classify(message, overlay);
        return kind == null ? null : new HistoryEntry.Message(kind, Instant.now(), codec.encode(message), null);
    }

    public static MessageKind classify(Component message, boolean overlay) {
        if (overlay || !(message.getContents() instanceof TranslatableContents contents)) return null;
        return classifyKey(contents.getKey());
    }

    public static MessageKind classifyKey(String key) {
        return switch (key) {
            case "multiplayer.player.joined", "multiplayer.player.joined.renamed" -> MessageKind.JOIN;
            case "multiplayer.player.left" -> MessageKind.LEAVE;
            case "chat.type.advancement.task", "chat.type.advancement.goal", "chat.type.advancement.challenge" -> MessageKind.ADVANCEMENT;
            default -> key.startsWith("death.attack.") || key.startsWith("death.fell.") ? MessageKind.DEATH : null;
        };
    }
}
