package io.github.iceean.serverchathistory.minecraft;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.HistorySnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Convert on the server thread; the persistence worker only sees immutable JSON. */
public final class MessageCodec {
    public boolean isPublicChat(net.minecraft.network.chat.ChatType.Bound bound) { return bound.chatType().is(net.minecraft.network.chat.ChatType.CHAT); }
    private final HolderLookup.Provider registries;
    public MessageCodec(HolderLookup.Provider registries) { this.registries = registries; }
    public String encode(Component component) {
        return ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow().toString();
    }
    public Component decode(String json) {
        return ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json))
                .getOrThrow();
    }
    public void validate(HistorySnapshot snapshot) {
        for (HistoryEntry entry : snapshot.entries()) {
            decode(entry.message().renderedJson());
            var chat = entry.message().chat();
            if (chat != null && chat.filteredJson() != null) decode(chat.filteredJson());
        }
    }
}
