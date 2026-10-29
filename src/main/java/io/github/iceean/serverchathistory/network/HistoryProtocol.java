package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.core.*;
import java.time.Instant;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class HistoryProtocol {
    public static final int VERSION = 1;
    private HistoryProtocol() { }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("server_chat_history", name));
    }
    private static void preferences(FriendlyByteBuf b, ReplayPreferences p) {
        b.writeBoolean(p.receiveHistory()); b.writeVarLong(p.historyLimit()); b.writeByte(p.events());
    }
    private static ReplayPreferences preferences(FriendlyByteBuf b) {
        return new ReplayPreferences(b.readBoolean(), b.readVarLong(), b.readUnsignedByte());
    }
    // The negotiation envelope stays stable across history protocol revisions.
    public record Hello(int version) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = id("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeVarInt(p.version), b -> new Hello(b.readVarInt()));
        public Type<Hello> type() { return TYPE; }
    }
    public record Preferences(int version, ReplayPreferences value) implements CustomPacketPayload {
        public static final Type<Preferences> TYPE = id("preferences");
        public static final StreamCodec<FriendlyByteBuf, Preferences> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeVarInt(p.version); preferences(b, p.value); },
                b -> new Preferences(b.readVarInt(), preferences(b)));
        public Type<Preferences> type() { return TYPE; }
    }
    public record Status(boolean negotiated, boolean enhanced, ReplayPreferences preferences, String reason) implements CustomPacketPayload {
        public static final Type<Status> TYPE = id("status_v1");
        public static final StreamCodec<FriendlyByteBuf, Status> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeBoolean(p.negotiated); b.writeBoolean(p.enhanced); HistoryProtocol.preferences(b, p.preferences); b.writeUtf(p.reason, 128); },
                b -> new Status(b.readBoolean(), b.readBoolean(), HistoryProtocol.preferences(b), b.readUtf(128)));
        public Type<Status> type() { return TYPE; }
    }
    public record Boundary(boolean start, int count) implements CustomPacketPayload {
        public static final Type<Boundary> TYPE = id("boundary_v1");
        public Boundary { if (count < 0) throw new IllegalArgumentException("Negative count"); }
        public static final StreamCodec<FriendlyByteBuf, Boundary> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeBoolean(p.start); b.writeVarInt(p.count); }, b -> new Boundary(b.readBoolean(), b.readVarInt()));
        public Type<Boundary> type() { return TYPE; }
    }
    public record Item(long sequence, MessageKind kind, Instant time, Component content, UUID senderId, String senderName) implements CustomPacketPayload {
        public static final Type<Item> TYPE = id("entry_v1");
        public Item {
            if (sequence < 1 || kind == null || time == null || content == null) throw new IllegalArgumentException("Invalid history item");
            if ((kind == MessageKind.CHAT) != (senderId != null) || (senderId == null) != (senderName == null))
                throw new IllegalArgumentException("Invalid sender");
            if (senderName != null && (senderName.isBlank() || senderName.length() > 16)) throw new IllegalArgumentException("Invalid name");
        }
        public static final StreamCodec<RegistryFriendlyByteBuf, Item> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeVarLong(p.sequence); b.writeEnum(p.kind); b.writeLong(p.time.toEpochMilli());
            ComponentSerialization.STREAM_CODEC.encode(b, p.content);
            if (p.kind == MessageKind.CHAT) { b.writeUUID(p.senderId); b.writeUtf(p.senderName, 16); }
        }, b -> {
            long sequence = b.readVarLong(); var kind = b.readEnum(MessageKind.class); var time = Instant.ofEpochMilli(b.readLong());
            var content = ComponentSerialization.STREAM_CODEC.decode(b);
            return new Item(sequence, kind, time, content, kind == MessageKind.CHAT ? b.readUUID() : null,
                    kind == MessageKind.CHAT ? b.readUtf(16) : null);
        });
        public Type<Item> type() { return TYPE; }
    }
    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Preferences.TYPE, Preferences.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Boundary.TYPE, Boundary.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Item.TYPE, Item.CODEC);
    }
}
