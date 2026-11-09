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

import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises capture, rich components and packet data without starting a game. */
class VanillaProtocolTest {
    private static RegistryAccess registries;
    private static MessageCodec codec;

    @BeforeAll static void bootstrapCodecs() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var chatTypes = new MappedRegistry<>(Registries.CHAT_TYPE, Lifecycle.stable());
        chatTypes.register(ChatType.CHAT, new ChatType(ChatTypeDecoration.withSender("chat.type.text"),
                ChatTypeDecoration.withSender("chat.type.text.narrate")), Lifecycle.stable());
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(chatTypes.freeze()));
        codec = new MessageCodec(registries);
    }

    @Test void richComponentsRoundTripWithoutFlattening() {
        var rich = Component.translatable("multiplayer.player.joined", Component.literal("Alice").withStyle(ChatFormatting.GOLD))
                .withStyle(style -> style.withHoverEvent(io.github.iceean.serverchathistory.minecraft.GameTestText.hover( Component.literal("details"))));
        assertEquals(rich, codec.decode(codec.encode(rich)));
    }

    @Test void captureStoresServerTimeAndRecipientSafeAlternatives() {
        UUID id = UUID.randomUUID();
        var sender = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        org.mockito.Mockito.when(sender.getUUID()).thenReturn(id);
        org.mockito.Mockito.when(sender.getGameProfile()).thenReturn(new GameProfile(id, "Alice"));
        var mask = new FilterMask(5); mask.setFiltered(1);
        var old = new PlayerChatMessage(SignedMessageLink.unsigned(id), null,
                new SignedMessageBody("hello", Instant.EPOCH, 0, LastSeenMessages.EMPTY), null, mask);
        var bound = ChatType.bind(ChatType.CHAT, registries, Component.literal("Alice"));
        var before = Instant.now();
        var captured = new MessageCapture(codec).chat(old, sender, bound);
        assertFalse(captured.time().isBefore(before)); assertFalse(captured.time().isAfter(Instant.now()));
        assertEquals(HistoryEntry.Filtering.PARTIAL, captured.chat().filtering());
        assertEquals("<Alice> hello", codec.decode(captured.renderedJson()).getString());
        assertTrue(codec.decode(captured.chat().filteredJson()).getString().contains("h#llo"));
        var full = new MessageCapture(codec).chat(old.filter(FilterMask.FULLY_FILTERED), sender, bound);
        assertEquals(HistoryEntry.Filtering.FULL, full.chat().filtering()); assertNull(full.chat().filteredJson());
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

    @Test void nonPlayerChatTypesAreRejectedBeforeAccessingSender() {
        var custom = new ChatType(ChatTypeDecoration.withSender("chat.type.emote"), ChatTypeDecoration.withSender("chat.type.emote"));
        var bound = new ChatType.Bound(custom, Component.literal("Alice"), null);
        assertNull(new MessageCapture(codec).chat(PlayerChatMessage.unsigned(UUID.randomUUID(), "command output"), null, bound));
    }

}
