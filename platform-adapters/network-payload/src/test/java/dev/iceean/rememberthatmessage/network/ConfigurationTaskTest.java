package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.ReplayPreferences;
import java.util.ArrayList;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationTaskTest {
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final ServerConfigurationPacketListenerImpl listener = mock(ServerConfigurationPacketListenerImpl.class);
    private final Connection connection = mock(Connection.class);

    @AfterEach void cleanup() { ServerHistoryNetworking.disconnect(listener, connection); }

    @Test void onlyStartedTasksOnTheirOwningServerCompleteOnceAfterReply() {
        when(connection.isConnected()).thenReturn(true);
        var task = ServerHistoryNetworking.queue(listener, server, connection);
        ServerHistoryNetworking.tick(server, Long.MAX_VALUE);
        verifyNoInteractions(listener);
        var packets = new ArrayList<Packet<?>>();
        task.start(packets::add);
        assertEquals(1, packets.size());
        assertEquals(new HistoryProtocol.Hello(HistoryProtocol.VERSION),
                ((ClientboundCustomPayloadPacket) packets.getFirst()).payload());
        ServerHistoryNetworking.reply(connection, new HistoryProtocol.Preferences(HistoryProtocol.VERSION, ReplayPreferences.DEFAULT));
        ServerHistoryNetworking.tick(mock(MinecraftServer.class), System.nanoTime());
        verifyNoInteractions(listener);
        ServerHistoryNetworking.tick(server, System.nanoTime());
        ServerHistoryNetworking.tick(server, System.nanoTime());
        verify(listener, times(1)).completeTask(task.type());
    }

    @Test void timeoutAdvancesConfigurationWithoutAReply() {
        when(connection.isConnected()).thenReturn(true);
        var task = ServerHistoryNetworking.queue(listener, server, connection);
        task.start(packet -> { });
        ServerHistoryNetworking.tick(server, System.nanoTime() + 2_000_000_000L);
        verify(listener).completeTask(task.type());
        ServerHistoryNetworking.reply(connection, new HistoryProtocol.Preferences(HistoryProtocol.VERSION, ReplayPreferences.DEFAULT));
        ServerHistoryNetworking.tick(server, System.nanoTime() + 3_000_000_000L);
        verifyNoMoreInteractions(listener);
    }

    @Test void disconnectedOrStoppedTasksNeverComplete() {
        for (int mode = 0; mode < 3; mode++) {
            var task = ServerHistoryNetworking.queue(listener, server, connection);
            task.start(packet -> { });
            when(connection.isConnected()).thenReturn(mode != 0);
            if (mode == 1) ServerHistoryNetworking.disconnect(listener, connection);
            if (mode == 2) ServerHistoryNetworking.stop(server);
            ServerHistoryNetworking.tick(server, System.nanoTime() + 2_000_000_000L);
        }
        verifyNoInteractions(listener);
    }
}
