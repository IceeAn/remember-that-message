package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.HistoryEntry;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import dev.iceean.rememberthatmessage.text.ConvertedMessageCache;
import java.util.Set;
import java.util.UUID;

/** 只隔离单条正文准备失败；网络发送由调用方执行，不能在这里重试。 */
public final class PortableReplayEncoder {
    public static final String PLACEHOLDER_JSON = "{\"translate\":\"remember_that_message.client.unavailable_entry\","
            + "\"fallback\":\"[Chat History] This record could not be displayed.\",\"color\":\"gray\"}";

    private PortableReplayEncoder() { }
    public record Encoded(byte[] bytes, Set<String> losses, RuntimeException failure) { }

    public static Encoded encode(UUID session, HistoryEntry entry, boolean filtered, ComponentFormat target,
                                 ConvertedMessageCache cache) {
        try {
            var body = cache.get(entry, filtered, target);
            return new Encoded(PortableProtocol.encode(item(session, entry, target, body.json(), !body.losses().isEmpty())),
                    body.losses(), null);
        } catch (RuntimeException failure) {
            // 不读取、截断或替换成未过滤原文；占位本身失败时仍向上传播。
            return new Encoded(PortableProtocol.encode(item(session, entry, target, PLACEHOLDER_JSON, true)), Set.of(), failure);
        }
    }
    private static PortableProtocol.Item item(UUID session, HistoryEntry entry, ComponentFormat target,
                                               String json, boolean degraded) {
        var message = entry.message(); var chat = message.chat();
        return new PortableProtocol.Item(session, entry.sequence(), message.kind(), message.time(), target, json,
                chat == null ? null : chat.senderId(), chat == null ? null : chat.senderName(), degraded);
    }
    public static boolean isPlaceholder(PortableProtocol.Item item) {
        return item.degraded() && PLACEHOLDER_JSON.equals(item.json());
    }
}
