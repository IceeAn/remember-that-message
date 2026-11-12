package dev.iceean.rememberthatmessage.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.*;
import org.objenesis.ObjenesisStd;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageLifecycleTest {
    private final List<ServerGamePacketListenerImpl> listeners = new ArrayList<>();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { listeners.forEach(GameTextFormat::disconnected); }

    private ServerGamePacketListenerImpl listener() {
        var listener = mock(ServerGamePacketListenerImpl.class); listeners.add(listener); return listener;
    }
    private ServerPlayer player(ServerGamePacketListenerImpl listener, int id) {
        var player = new ObjenesisStd().newInstance(ServerPlayer.class);
        player.setId(id); player.connection = listener; listener.player = player;
        return player;
    }
    @Test void respawnRetainsLanguageWithoutAnotherClientSettingsPacket() throws Exception {
        var listener = listener(); var old = player(listener, 42);
        GameTextFormat.language(old, "zh_cn");
        var replacement = player(listener, 42);
        assertEquals(old, replacement); assertNotSame(old, replacement);
        assertEquals("zh_cn", GameTextFormat.language(replacement));
        var field = GameTextFormat.class.getDeclaredField("LANGUAGES"); field.setAccessible(true);
        assertEquals(Set.of(listener), ((Map<?, ?>) field.get(null)).keySet());
        GameTextFormat.language(replacement, "zh_tw");
        assertEquals("zh_tw", GameTextFormat.language(replacement));
    }
    @Test void newConnectionsDoNotInheritLanguageEvenWithEqualEntityIds() {
        var firstListener = listener(); var secondListener = listener();
        var first = player(firstListener, 42); var second = player(secondListener, 42);
        GameTextFormat.language(first, "zh_cn");
        assertEquals("en_us", GameTextFormat.language(second));
        GameTextFormat.language(second, "ja_jp");
        GameTextFormat.disconnected(firstListener);
        assertEquals("en_us", GameTextFormat.language(first));
        assertEquals("ja_jp", GameTextFormat.language(second));
    }
    @Test void stoppingOneServerDoesNotDiscardAnotherServersLanguage() {
        var firstServer = mock(MinecraftServer.class); var secondServer = mock(MinecraftServer.class);
        var first = ownedPlayer(firstServer); var second = ownedPlayer(secondServer);
        GameTextFormat.language(first, "zh_cn"); GameTextFormat.language(second, "ja_jp");
        GameTextFormat.stopped(firstServer);
        assertEquals("en_us", GameTextFormat.language(first));
        assertEquals("ja_jp", GameTextFormat.language(second));
    }
    private ServerPlayer ownedPlayer(MinecraftServer server) {
        var listener = listener();
        var level = mock(ServerLevel.class); when(level.getServer()).thenReturn(server);
        var player = mock(ServerPlayer.class); when(player.level()).thenReturn(level);
        player.connection = listener; listener.player = player;
        return player;
    }
}
