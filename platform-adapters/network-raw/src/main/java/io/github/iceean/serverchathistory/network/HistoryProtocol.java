package io.github.iceean.serverchathistory.network;
import io.github.iceean.serverchathistory.core.*;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.network.chat.Component;
public final class HistoryProtocol {
    private HistoryProtocol() { }
    public static void register() { GameNetworking.register(); }
    public record Status(boolean negotiated, boolean enhanced, ReplayPreferences preferences, String reason) implements ProtocolData.Status { }
    public record Item(long sequence, MessageKind kind, Instant time, Component content, UUID senderId, String senderName) implements ProtocolData.Item<Component> {
        public Item { ProtocolData.validateItem(sequence, kind, time, content, senderId, senderName); }
    }
}
