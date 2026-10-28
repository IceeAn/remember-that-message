package io.github.iceean.serverchathistory.minecraft;

import com.mojang.serialization.Lifecycle;
import io.github.iceean.serverchathistory.core.*;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplayMessageCacheTest {
    private static RegistryAccess.Frozen registries;
    private static MessageCodec codec;
    private static final UUID SENDER = UUID.randomUUID();

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var types = new MappedRegistry<>(Registries.CHAT_TYPE, Lifecycle.stable());
        types.register(ChatType.CHAT, new ChatType(ChatTypeDecoration.withSender("chat.type.text"),
                ChatTypeDecoration.withSender("chat.type.text.narrate")), RegistrationInfo.BUILT_IN);
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(types.freeze())).freeze();
        codec = new MessageCodec(registries);
    }

    private HistoryEntry entry(long sequence, String text) {
        var mask = new FilterMask(text.length()); mask.setFiltered(1);
        var chat = new HistoryEntry.Chat(SENDER, "Alice", text, codec.encode(Component.literal(text)),
                codec.encode(Component.literal("Alice")), MessageCodec.encodeMask(mask), false);
        return new HistoryEntry(sequence, new HistoryEntry.Message(MessageKind.CHAT, Instant.EPOCH,
                codec.encode(Component.literal("<Alice> " + text)), chat));
    }

    @Test void cachedComponentsAndMasksAreReusedWithoutFlatteningText() {
        var cache = new ReplayMessageCache(codec); var entry = entry(1, "hello");
        var decoded = cache.get(entry);
        assertSame(decoded.rendered(), cache.get(entry).rendered());
        assertSame(decoded.body(), cache.get(entry).body());
        assertSame(decoded.name(), cache.get(entry).name());
        assertSame(decoded.mask(), cache.get(entry).mask());
        assertEquals(codec.decode(entry.message().renderedJson()), decoded.rendered());
        assertEquals("h#llo", decoded.mask().apply("hello"));
    }

    @Test void richEventComponentsKeepTheirTranslationAndHoverData() {
        var rich = Component.translatable("multiplayer.player.joined", Component.literal("Alice").withStyle(ChatFormatting.GOLD))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal("details"))));
        var entry = new HistoryEntry(1, new HistoryEntry.Message(MessageKind.JOIN, Instant.EPOCH, codec.encode(rich), null));
        var cache = new ReplayMessageCache(codec);
        assertEquals(rich, cache.get(entry).rendered());
        assertEquals(codec.encode(rich), codec.encode(cache.get(entry).rendered()));
    }

    @Test void cachedUnsignedBodyPreservesTimestampFilterAndConnectionSpecificIndex() {
        var entry = entry(1, "hello"); var cache = new ReplayMessageCache(codec); var decoded = cache.get(entry);
        for (int index : new int[]{7, 99}) {
            var message = VanillaReplayTransport.unsigned(entry.message(), decoded.body(), decoded.mask());
            var packet = new ClientboundPlayerChatPacket(index, SENDER, message.link().index(), Optional.empty(),
                    message.signedBody().pack(MessageSignatureCache.createDefault()), Optional.of(message.decoratedContent()),
                    message.filterMask(), ChatType.bind(ChatType.CHAT, registries, decoded.name()));
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                ClientboundPlayerChatPacket.STREAM_CODEC.encode(bytes, packet);
                var restored = ClientboundPlayerChatPacket.STREAM_CODEC.decode(bytes);
                assertEquals(index, restored.globalIndex());
                assertEquals(Instant.EPOCH, restored.body().timeStamp());
                assertEquals(decoded.body(), restored.unsignedContent().orElseThrow());
                assertEquals(decoded.mask(), restored.filterMask());
                assertTrue(restored.signature().isEmpty());
            } finally { bytes.release(); }
        }
    }

    @Test void evictionShrinkAndClearReleaseDecodedEntries() {
        var cache = new ReplayMessageCache(codec);
        var buffer = new HistoryBuffer(HistorySnapshot.empty(), 2);
        buffer.append(entry(1, "one").message()); buffer.append(entry(2, "two").message());
        var first = buffer.snapshot().entries().getFirst();
        var old = cache.get(first);
        cache.get(buffer.snapshot().entries().getLast());
        buffer.append(entry(3, "three").message()); cache.retain(buffer.snapshot());
        assertEquals(1, cache.size());
        cache.get(buffer.snapshot().entries().getLast());
        buffer.resize(1); cache.retain(buffer.snapshot()); assertEquals(1, cache.size());
        buffer.clear(); cache.retain(buffer.snapshot()); assertEquals(0, cache.size());
        assertNotSame(old, cache.get(first));
        cache.clear(); assertEquals(0, cache.size());
    }

    @Test void cachesAreWorldLocalAndSequenceReuseCannotReturnAnotherMessage() {
        var first = new ReplayMessageCache(codec); var second = new ReplayMessageCache(codec);
        var one = entry(1, "one"); var other = entry(1, "other");
        assertNotSame(first.get(one).rendered(), second.get(one).rendered());
        assertEquals("<Alice> other", first.get(other).rendered().getString());
        assertEquals("<Alice> one", second.get(one).rendered().getString());
    }

    @Test void onlyRequestedFieldsAreDecoded() {
        var chat = new HistoryEntry.Chat(SENDER, "Alice", "hello", "{}", "{}", "{}", false);
        var entry = new HistoryEntry(1, new HistoryEntry.Message(MessageKind.CHAT, Instant.EPOCH, "\"display\"", chat));
        assertEquals("display", new ReplayMessageCache(codec).get(entry).rendered().getString());
    }

    @Test void cachedReplayStillAppliesFilteringPerRecipientAndPreservesSelfException() {
        var server = mock(MinecraftServer.class); when(server.registryAccess()).thenReturn(registries);
        var cache = new ReplayMessageCache(codec); var transport = new VanillaReplayTransport(server, cache);
        var entry = entry(1, "hello");
        var received = new ArrayList<Component>();
        var player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getChatVisibility()).thenReturn(ChatVisiblity.FULL);
        doAnswer(call -> { received.add(call.getArgument(0)); return null; }).when(player).sendSystemMessage(any(Component.class));
        transport.send(player, transport.visibleTo(player, List.of(entry)), false);
        var original = received.getLast();
        when(player.isTextFilteringEnabled()).thenReturn(true);
        transport.send(player, transport.visibleTo(player, List.of(entry)), false);
        assertTrue(received.getLast().getString().contains("h#llo"));
        when(player.getUUID()).thenReturn(SENDER);
        transport.send(player, transport.visibleTo(player, List.of(entry)), false);
        assertEquals(original, received.getLast());
        assertEquals("<Alice> hello", original.getString());
    }
}
