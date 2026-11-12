package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.text.*;
import java.io.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

/** 自定义通道内部只使用固定二进制字段及 UTF-8，不嵌入游戏注册表编码。 */
public final class PortableProtocol {
    public static final int VERSION = 2;
    public static final int MAX_FRAME = 300000;
    private PortableProtocol() { }
    public sealed interface Frame permits Hello, Reply, Status, Boundary, Item { UUID session(); }
    public record Hello(UUID session, int version, ComponentFormat format) implements Frame { }
    public record Reply(UUID session, int version, ComponentFormat format, ReplayPreferences preferences) implements Frame { }
    public record Status(UUID session, boolean enhanced, ReplayPreferences preferences, String reason) implements Frame { }
    public record Boundary(UUID session, boolean start, int count) implements Frame {
        public Boundary { ProtocolData.validateBoundary(count); }
    }
    public record Item(UUID session, long sequence, MessageKind kind, Instant time, ComponentFormat format,
                       String json, UUID senderId, String senderName, boolean degraded) implements Frame {
        public Item { ProtocolData.validateItem(sequence, kind, time, json, senderId, senderName); Objects.requireNonNull(format); }
    }
    public static byte[] encode(Frame frame) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeByte(frame instanceof Hello ? 0 : frame instanceof Reply ? 1 : frame instanceof Status ? 2 : frame instanceof Boundary ? 3 : 4);
            uuid(out, frame.session());
            if (frame instanceof Hello h) { out.writeInt(h.version()); string(out, h.format().name(), 64); }
            else if (frame instanceof Reply r) { out.writeInt(r.version()); string(out, r.format().name(), 64); preferences(out, r.preferences()); }
            else if (frame instanceof Status s) { out.writeBoolean(s.enhanced()); preferences(out, s.preferences()); string(out, s.reason(), 128); }
            else if (frame instanceof Boundary b) { out.writeBoolean(b.start()); out.writeInt(b.count()); }
            else if (frame instanceof Item i) {
                out.writeLong(i.sequence()); string(out, i.kind().name(), 32); out.writeLong(i.time().toEpochMilli());
                string(out, i.format().name(), 64); string(out, i.json(), TextConversion.MAX_TEXT_BYTES);
                if (i.kind() == MessageKind.CHAT) { uuid(out, i.senderId()); string(out, i.senderName(), 64); }
                out.writeBoolean(i.degraded());
            }
            if (bytes.size() > MAX_FRAME) throw new IllegalArgumentException("History frame too large");
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalArgumentException("Cannot encode history frame", failure); }
    }
    public static Frame decode(byte[] bytes) {
        if (bytes.length > MAX_FRAME) throw new IllegalArgumentException("History frame too large");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes)); int tag = in.readUnsignedByte(); UUID session = uuid(in);
            Frame result = switch (tag) {
                case 0 -> new Hello(session, in.readInt(), ComponentFormat.valueOf(string(in, 64)));
                case 1 -> new Reply(session, in.readInt(), ComponentFormat.valueOf(string(in, 64)), preferences(in));
                case 2 -> new Status(session, in.readBoolean(), preferences(in), string(in, 128));
                case 3 -> new Boundary(session, in.readBoolean(), in.readInt());
                case 4 -> {
                    long sequence = in.readLong(); var kind = MessageKind.valueOf(string(in, 32)); var time = Instant.ofEpochMilli(in.readLong());
                    var format = ComponentFormat.valueOf(string(in, 64)); String json = string(in, TextConversion.MAX_TEXT_BYTES);
                    UUID sender = kind == MessageKind.CHAT ? uuid(in) : null; String name = sender == null ? null : string(in, 64);
                    yield new Item(session, sequence, kind, time, format, json, sender, name, in.readBoolean());
                }
                default -> throw new IllegalArgumentException("Unknown history frame type");
            };
            if (in.available() != 0) throw new IllegalArgumentException("Trailing history frame bytes");
            return result;
        } catch (IOException failure) { throw new IllegalArgumentException("Invalid history frame", failure); }
    }
    private static void preferences(DataOutputStream out, ReplayPreferences value) throws IOException {
        out.writeBoolean(value.receiveHistory()); out.writeLong(value.historyLimit()); out.writeInt(value.events());
    }
    private static ReplayPreferences preferences(DataInputStream in) throws IOException {
        return new ReplayPreferences(in.readBoolean(), in.readLong(), in.readInt());
    }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void string(DataOutputStream out, String value, int maximum) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximum) throw new IllegalArgumentException("History string too long");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximum || length > in.available()) throw new IllegalArgumentException("Invalid history string length");
        byte[] bytes = in.readNBytes(length);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
}
