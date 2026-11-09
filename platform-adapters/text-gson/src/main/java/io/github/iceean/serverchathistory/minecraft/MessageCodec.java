package io.github.iceean.serverchathistory.minecraft;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.HistorySnapshot;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;


/** Convert on the server thread; the persistence worker only sees immutable JSON. */
public final class MessageCodec {
    public boolean isPublicChat(net.minecraft.network.chat.ChatType.Bound bound) { return isPublicChatType(bound.chatType()); }
    private final RegistryAccess registries;
    public MessageCodec(RegistryAccess registries) { this.registries = registries; }
    public boolean isPublicChatType(net.minecraft.network.chat.ChatType type) {
        return registries.registryOrThrow(net.minecraft.core.registries.Registries.CHAT_TYPE).get(net.minecraft.network.chat.ChatType.CHAT) == type;
    }
    public String encode(Component component) {
        return Component.Serializer.toJson(component);
    }
    public Component decode(String json) {
        return java.util.Objects.requireNonNull(Component.Serializer.fromJson(json), "Null component");
    }
    public void validate(HistorySnapshot snapshot) {
        for (HistoryEntry entry : snapshot.entries()) {
            decode(entry.message().renderedJson());
            var chat = entry.message().chat();
            if (chat != null && chat.filteredJson() != null) decode(chat.filteredJson());
        }
    }
}
