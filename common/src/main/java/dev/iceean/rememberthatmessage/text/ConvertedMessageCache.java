package dev.iceean.rememberthatmessage.text;

import dev.iceean.rememberthatmessage.core.*;
import java.util.*;

/** 服务端线程使用；同编码直接返回原文，转换结果不写入历史文件。 */
public final class ConvertedMessageCache {
    private record Key(long sequence, boolean filtered, ComponentFormat target) { }
    private record Cached(HistoryEntry.Message source, TextConversion.Result value) { }
    private final ComponentFormat source;
    private final Map<Key, Cached> entries = new HashMap<>();
    public ConvertedMessageCache(ComponentFormat source) { this.source = Objects.requireNonNull(source); }
    public TextConversion.Result get(HistoryEntry entry, boolean filtered, ComponentFormat target) {
        var message = entry.message();
        String json = filtered ? Objects.requireNonNull(message.chat()).filteredJson() : message.renderedJson();
        Objects.requireNonNull(json, "No recipient-safe alternative");
        if (target == source) return new TextConversion.Result(json, Set.of());
        var key = new Key(entry.sequence(), filtered, target);
        var old = entries.get(key);
        if (old != null && old.source == message) return old.value;
        var converted = TextConversion.convert(json, source, target);
        entries.put(key, new Cached(message, converted));
        return converted;
    }
    public void retain(HistorySnapshot snapshot) {
        long first = snapshot.entries().isEmpty() ? snapshot.nextSequence() : snapshot.entries().get(0).sequence();
        entries.keySet().removeIf(key -> key.sequence < first || key.sequence >= snapshot.nextSequence());
    }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
}
