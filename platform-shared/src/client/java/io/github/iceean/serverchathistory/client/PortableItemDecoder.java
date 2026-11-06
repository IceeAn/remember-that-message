package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.minecraft.GameTextFormat;
import io.github.iceean.serverchathistory.minecraft.MessageCodec;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import io.github.iceean.serverchathistory.network.ClientPlaySession;
import io.github.iceean.serverchathistory.network.PortableProtocol;
import io.github.iceean.serverchathistory.network.PortableReplayEncoder;
import io.github.iceean.serverchathistory.text.ComponentFormat;
import io.github.iceean.serverchathistory.text.TextConversion;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 只处理正文；显示开始后不捕获并重试，避免重复插入聊天。 */
public final class PortableItemDecoder {
    private PortableItemDecoder() { }
    public record Decoded(HistoryProtocol.Item item, boolean placeholder, boolean simplified) { }

    /** 调用前已消费协议及显示窗口的序号；正常过滤不解析，也不计为内容失败。 */
    public static void receive(PortableProtocol.Item value, ClientPlaySession session, boolean allowed,
                               Supplier<MessageCodec> codec, Consumer<Decoded> display) {
        if (!allowed) return;
        try {
            var decoded = decode(value, codec.get());
            display.accept(decoded);
            if (decoded.placeholder()) session.unavailable();
        } catch (RuntimeException failure) {
            session.unavailable();
            throw failure;
        }
    }

    public static Decoded decode(PortableProtocol.Item value, MessageCodec codec) {
        Component content;
        boolean placeholder = PortableReplayEncoder.isPlaceholder(value), simplified = false;
        try {
            var target = GameTextFormat.CURRENT.componentEncoding();
            if (value.format() != target && value.format() != ComponentFormat.PORTABLE_V1)
                throw new IllegalArgumentException("Unexpected history component encoding");
            var body = TextConversion.convert(value.json(), value.format(), target);
            content = codec.decode(body.json());
            simplified = value.degraded() || !body.losses().isEmpty();
        } catch (RuntimeException failure) {
            content = codec.decode(PortableReplayEncoder.PLACEHOLDER_JSON);
            placeholder = true;
        }
        return new Decoded(new HistoryProtocol.Item(value.sequence(), value.kind(), value.time(), content,
                value.senderId(), value.senderName()), placeholder, !placeholder && simplified);
    }
    /** 接收限制仍检查原始 item；仅向显示联动隐藏占位的玩家身份。 */
    public static HistoryProtocol.Item presentation(HistoryProtocol.Item item, boolean placeholder) {
        return placeholder ? new HistoryProtocol.Item(item.sequence(), MessageKind.JOIN, item.time(), item.content(), null, null) : item;
    }
}
