package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.core.ReplayPreferences;
import java.time.Instant;
import java.util.UUID;

/** Business contracts implemented directly by platform payloads, without wrapper allocation. */
public final class ProtocolData {
    public static final int VERSION = 1;
    private ProtocolData() { }
    public interface Hello { int version(); }
    public interface Preferences { int version(); ReplayPreferences value(); }
    public interface Status {
        boolean negotiated(); boolean enhanced(); ReplayPreferences preferences(); String reason();
    }
    public interface Boundary { boolean start(); int count(); }
    public interface Item<C> {
        long sequence(); MessageKind kind(); Instant time(); C content(); UUID senderId(); String senderName();
    }
    public static void validateBoundary(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative count");
    }
    public static void validateItem(long sequence, MessageKind kind, Instant time, Object content, UUID senderId, String senderName) {
        if (sequence < 1 || kind == null || time == null || content == null) throw new IllegalArgumentException("Invalid history item");
        if ((kind == MessageKind.CHAT) != (senderId != null) || (senderId == null) != (senderName == null))
            throw new IllegalArgumentException("Invalid sender");
        if (senderName != null && (senderName.isBlank() || senderName.length() > 16)) throw new IllegalArgumentException("Invalid name");
    }
}
