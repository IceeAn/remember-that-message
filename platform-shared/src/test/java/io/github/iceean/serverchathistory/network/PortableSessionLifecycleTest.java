package io.github.iceean.serverchathistory.network;

import io.github.iceean.serverchathistory.core.ReplayPreferences;
import io.github.iceean.serverchathistory.minecraft.GameTextFormat;
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

class PortableSessionLifecycleTest {
    private final List<ServerGamePacketListenerImpl> listeners = new ArrayList<>();

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { listeners.forEach(PortableServerNetworking::disconnected); }

    private ServerGamePacketListenerImpl listener() {
        var listener = mock(ServerGamePacketListenerImpl.class);
        listeners.add(listener);
        return listener;
    }
    private ServerPlayer player(ServerGamePacketListenerImpl listener, int id) {
        var player = new ObjenesisStd().newInstance(ServerPlayer.class);
        player.setId(id);
        player.connection = listener;
        listener.player = player;
        return player;
    }
    private PlaySession accepted(ServerGamePacketListenerImpl listener, ReplayPreferences preferences) {
        var state = PortableServerNetworking.joined(listener);
        assertTrue(state.offer());
        state.reply(new PortableProtocol.Reply(state.id(), PortableProtocol.VERSION,
                GameTextFormat.CURRENT.componentEncoding(), preferences));
        return state;
    }
    @Test void respawnKeepsSessionTokenAndDisabledReplayOnTheLiveConnection() throws Exception {
        var listener = listener();
        var old = player(listener, 42);
        var preferences = new ReplayPreferences(false, 5, 0);
        var state = accepted(listener, preferences);
        var replacement = player(listener, 42);
        assertNotSame(old, replacement);
        assertEquals(old, replacement);
        assertSame(state, PortableServerNetworking.session(replacement));
        assertEquals(preferences, PortableServerNetworking.session(replacement).preferences());
        assertTrue(PortableServerNetworking.active(replacement));
        // 确定性检查弱键的可达性，不让回归测试依赖 GC 的执行时机。
        var field = PortableServerNetworking.class.getDeclaredField("SESSIONS");
        field.setAccessible(true);
        assertEquals(Set.of(listener), ((Map<?, ?>) field.get(null)).keySet());
    }
    @Test void pendingNegotiationAlsoSurvivesPlayerReplacement() {
        var listener = listener(); player(listener, 42);
        var state = PortableServerNetworking.joined(listener); state.offer();
        var replacement = player(listener, 42);
        assertSame(state, PortableServerNetworking.session(replacement));
        var preferences = new ReplayPreferences(false, 0, 0);
        PortableServerNetworking.session(replacement).reply(new PortableProtocol.Reply(state.id(), PortableProtocol.VERSION,
                GameTextFormat.CURRENT.componentEncoding(), preferences));
        assertTrue(PortableServerNetworking.active(replacement));
        assertEquals(preferences, state.preferences());
    }
    @Test void equalEntityIdsOnDifferentConnectionsDoNotSharePreferences() {
        var firstListener = listener(); var secondListener = listener();
        var first = player(firstListener, 42); var second = player(secondListener, 42);
        assertEquals(first, second);
        var disabled = accepted(firstListener, new ReplayPreferences(false, 0, 0));
        var enabled = accepted(secondListener, ReplayPreferences.DEFAULT);
        assertSame(disabled, PortableServerNetworking.session(first));
        assertSame(enabled, PortableServerNetworking.session(second));
        assertNotEquals(disabled.id(), enabled.id());
        PortableServerNetworking.disconnected(firstListener);
        assertNull(PortableServerNetworking.session(first));
        assertSame(enabled, PortableServerNetworking.session(second));
        var rejoined = PortableServerNetworking.joined(firstListener);
        assertNotEquals(disabled.id(), rejoined.id());
        assertFalse(rejoined.accepted());
    }
    @Test void stoppingOneLogicalServerOnlyClearsItsConnectionsAndWarnings() throws Exception {
        var firstServer = mock(MinecraftServer.class); var secondServer = mock(MinecraftServer.class);
        var first = ownedListener(firstServer); var second = ownedListener(secondServer);
        accepted(first, ReplayPreferences.DEFAULT); accepted(second, ReplayPreferences.DEFAULT);
        var field = PortableServerNetworking.class.getDeclaredField("WARNED"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var warned = (Set<ServerGamePacketListenerImpl>) field.get(null);
        var failedField = PortableServerNetworking.class.getDeclaredField("FAILED"); failedField.setAccessible(true);
        @SuppressWarnings("unchecked") var failed = (Set<ServerGamePacketListenerImpl>) failedField.get(null);
        warned.add(first); warned.add(second);
        failed.add(first); failed.add(second);
        PortableServerNetworking.stopped(firstServer);
        assertNull(PortableServerNetworking.session(first.player));
        assertNotNull(PortableServerNetworking.session(second.player));
        assertEquals(Set.of(second), warned);
        assertEquals(Set.of(second), failed);
        PortableServerNetworking.disconnected(second);
        assertNull(PortableServerNetworking.session(second.player)); assertTrue(warned.isEmpty());
        assertTrue(failed.isEmpty());
    }
    private ServerGamePacketListenerImpl ownedListener(MinecraftServer server) throws Exception {
        var listener = listener();
        var level = mock(ServerLevel.class); when(level.getServer()).thenReturn(server);
        var player = new ObjenesisStd().newInstance(ServerPlayer.class);
        // 某些版本将 level() 设为 final，设置测试实体字段以调用真实 getter。
        var levelField = net.minecraft.world.entity.Entity.class.getDeclaredField("level");
        levelField.setAccessible(true); levelField.set(player, level);
        player.connection = listener; listener.player = player;
        return listener;
    }
}
