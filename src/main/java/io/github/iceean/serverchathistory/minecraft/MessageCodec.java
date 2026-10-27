package io.github.iceean.serverchathistory.minecraft;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.core.HistorySnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FilterMask;

/** Convert on the server thread; the persistence worker only sees immutable JSON. */
public final class MessageCodec {
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
    public static String encodeMask(FilterMask mask) {
        return FilterMask.CODEC.encodeStart(JsonOps.INSTANCE, mask).getOrThrow().toString();
    }
    public static FilterMask decodeMask(String json) {
        return FilterMask.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
    public void validate(HistorySnapshot snapshot) {
        for (HistoryEntry entry : snapshot.entries()) {
            decode(entry.message().renderedJson());
            if (entry.message().chat() != null) {
                var chat = entry.message().chat();
                decode(chat.bodyJson());
                decode(chat.nameJson());
                decodeMask(chat.filterMaskJson());
            }
        }
    }
}
