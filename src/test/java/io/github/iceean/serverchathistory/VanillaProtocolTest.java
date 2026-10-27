package io.github.iceean.serverchathistory;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Lifecycle;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.storage.JsonSupport;
import io.netty.buffer.Unpooled;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises codecs and packet data only. Does not start a game, world, or server. */
class VanillaProtocolTest {
    private static RegistryAccess registries;
    private static MessageCodec codec;

    @BeforeAll static void bootstrapCodecs() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var chatTypes = new MappedRegistry<>(Registries.CHAT_TYPE, Lifecycle.stable());
        chatTypes.register(ChatType.CHAT, new ChatType(ChatTypeDecoration.withSender("chat.type.text"),
                ChatTypeDecoration.withSender("chat.type.text.narrate")), RegistrationInfo.BUILT_IN);
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(chatTypes.freeze()));
        codec = new MessageCodec(registries);
    }

    private static HistoryEntry.Chat chat(UUID id) {
        return new HistoryEntry.Chat(id, "Alice", "hello", codec.encode(Component.literal("hello").withStyle(ChatFormatting.AQUA)),
                codec.encode(Component.literal("Alice")), MessageCodec.encodeMask(FilterMask.PASS_THROUGH), false);
    }

    @Test void nativePlayerPacketRoundTripRetainsHistoricalTimeAndNoSignature() {
        var data = chat(UUID.randomUUID());
        var time = Instant.parse("2026-09-15T12:34:56.123Z");
        var message = new HistoryEntry.Message(MessageKind.CHAT, time, codec.encode(Component.literal("<Alice> hello")), data);
        var restored = JsonSupport.GSON.fromJson(JsonSupport.GSON.toJson(message), HistoryEntry.Message.class);
        var playerChat = VanillaReplayTransport.unsigned(restored, codec.decode(data.bodyJson()), FilterMask.PASS_THROUGH);
        assertEquals(time, playerChat.timeStamp());
        assertNull(playerChat.signature());
        var original = new ClientboundPlayerChatPacket(17, data.senderId(), playerChat.link().index(), Optional.empty(),
                playerChat.signedBody().pack(MessageSignatureCache.createDefault()), Optional.of(playerChat.decoratedContent()),
                playerChat.filterMask(), ChatType.bind(ChatType.CHAT, registries, codec.decode(data.nameJson())));
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ClientboundPlayerChatPacket.STREAM_CODEC.encode(bytes, original);
            var decoded = ClientboundPlayerChatPacket.STREAM_CODEC.decode(bytes);
            assertEquals(17, decoded.globalIndex());
            assertEquals(time, decoded.body().timeStamp());
            assertEquals(data.senderId(), decoded.sender());
            assertTrue(decoded.signature().isEmpty());
            assertEquals(playerChat.decoratedContent(), decoded.unsignedContent().orElseThrow());
        } finally { bytes.release(); }
    }

    @Test void richComponentsAndFilterMasksRoundTripWithoutFlattening() {
        var rich = Component.translatable("multiplayer.player.joined", Component.literal("Alice").withStyle(ChatFormatting.GOLD))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal("details"))));
        assertEquals(rich, codec.decode(codec.encode(rich)));
        var mask = new FilterMask(5);
        mask.setFiltered(1);
        assertEquals(mask, MessageCodec.decodeMask(MessageCodec.encodeMask(mask)));
        assertEquals("h#llo", mask.apply("hello"));
    }

    @Test void onlyWhitelistedPublicEventComponentsAreClassified() {
        assertEquals(MessageKind.JOIN, MessageCapture.classify(Component.translatable("multiplayer.player.joined", "Alice"), false));
        assertEquals(MessageKind.LEAVE, MessageCapture.classifyKey("multiplayer.player.left"));
        assertEquals(MessageKind.DEATH, MessageCapture.classifyKey("death.attack.player"));
        assertEquals(MessageKind.ADVANCEMENT, MessageCapture.classifyKey("chat.type.advancement.challenge"));
        assertNull(MessageCapture.classify(Component.literal("Alice joined the game"), false));
        assertNull(MessageCapture.classify(Component.translatable("multiplayer.player.joined", "Alice"), true));
        for (var key : List.of("chat.type.announcement", "chat.type.emote", "commands.message.display.incoming", "chat.type.team.text")) {
            assertNull(MessageCapture.classifyKey(key));
        }
    }

    @Test void senderAndRecipientFilteringMatchVanillaSelfMessageException() {
        UUID sender = UUID.randomUUID();
        var plain = chat(sender);
        var filtered = new HistoryEntry.Chat(sender, plain.senderName(), plain.text(), plain.bodyJson(), plain.nameJson(), plain.filterMaskJson(), true);
        assertFalse(VanillaReplayTransport.needsFiltering(filtered, sender, true));
        assertTrue(VanillaReplayTransport.needsFiltering(filtered, UUID.randomUUID(), false));
        assertTrue(VanillaReplayTransport.needsFiltering(plain, UUID.randomUUID(), true));
        assertFalse(VanillaReplayTransport.needsFiltering(plain, UUID.randomUUID(), false));
    }

    @Test void nonPlayerChatTypesAreRejectedBeforeAccessingSender() {
        var custom = new ChatType(ChatTypeDecoration.withSender("chat.type.emote"), ChatTypeDecoration.withSender("chat.type.emote"));
        var bound = new ChatType.Bound(Holder.direct(custom), Component.literal("Alice"), Optional.empty());
        assertNull(new MessageCapture(codec).chat(PlayerChatMessage.unsigned(UUID.randomUUID(), "command output"), null, bound));
    }

    @Test void offlineIdentitiesAreUnlistedAndOnlineSessionsRestoredEvenOnFailure() throws Exception {
        var offline = chat(UUID.randomUUID());
        var online = chat(UUID.randomUUID());
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var session = new RemoteChatSession.Data(UUID.randomUUID(), new ProfilePublicKey.Data(
                Instant.parse("2027-01-01T00:00:00Z"), keys.getPublic(), new byte[256]));
        var current = new Entry(online.senderId(), new GameProfile(online.senderId(), "NewName"), true,
                42, GameType.CREATIVE, Component.literal("Current"), true, 3, session);
        var packets = new ArrayList<Packet<?>>();
        var lease = new PlayerInfoLease(id -> id.equals(online.senderId()) ? current : null, packets::add,
                VanillaProtocolTest::untransformedPacket);
        assertThrows(IllegalStateException.class, () -> lease.run(List.of(offline, online, offline), () -> {
            assertEquals(2, packets.size());
            throw new IllegalStateException("simulated replay failure");
        }));
        assertEquals(4, packets.size());
        var add = (ClientboundPlayerInfoUpdatePacket) packets.get(0);
        assertEquals(EnumSet.of(Action.ADD_PLAYER, Action.INITIALIZE_CHAT), add.actions());
        assertFalse(add.entries().getFirst().listed());
        assertNull(add.entries().getFirst().chatSession());
        var cleared = (ClientboundPlayerInfoUpdatePacket) packets.get(1);
        assertEquals(EnumSet.of(Action.INITIALIZE_CHAT), cleared.actions());
        assertNull(cleared.entries().getFirst().chatSession());
        var restored = (ClientboundPlayerInfoUpdatePacket) packets.get(2);
        assertEquals(List.of(current), restored.entries());
        var decoded = roundTripInfo(restored);
        assertEquals(session, decoded.entries().getFirst().chatSession());
        assertEquals(List.of(offline.senderId()), ((ClientboundPlayerInfoRemovePacket) packets.get(3)).profileIds());
        var offlineDecoded = roundTripInfo(add);
        assertFalse(offlineDecoded.entries().getFirst().listed());
        assertEquals(offline.senderId(), offlineDecoded.entries().getFirst().profileId());
    }

    private static ClientboundPlayerInfoUpdatePacket roundTripInfo(ClientboundPlayerInfoUpdatePacket packet) {
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(bytes, packet);
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(bytes);
        } finally { bytes.release(); }
    }

    private static ClientboundPlayerInfoUpdatePacket untransformedPacket(EnumSet<Action> actions, List<Entry> entries) {
        // JUnit does not launch Fabric/Mixin. Access the same target field to test
        // native packet codecs; actual Mixin application is a separate live check.
        try {
            var packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
            var field = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
            field.setAccessible(true);
            field.set(packet, List.copyOf(entries));
            return packet;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
