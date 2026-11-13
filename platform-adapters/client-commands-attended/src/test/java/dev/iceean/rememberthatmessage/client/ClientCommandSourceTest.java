package dev.iceean.rememberthatmessage.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCommandSourceTest {
    @ParameterizedTest
    @ValueSource(strings = {"rememberthatmessageclient", "rememberthatmessageclient status", "rememberthatmessageclient reload",
            "rememberthatmessageclient config get receiveHistory", "rememberthatmessageclient config set receiveHistory false"})
    void actualCommandTreeRequiresAnAttendedSource(String command) {
        var source = ClientCommandTestSupport.source();
        when(source.attended()).thenReturn(false);
        var restricted = ClientCommandTestSupport.parse(command, source);
        assertFalse(ClientCommandTestSupport.executable(restricted));
        assertNull(restricted.getContext().getCommand());
        when(source.attended()).thenReturn(true);
        assertTrue(ClientCommandTestSupport.executable(ClientCommandTestSupport.parse(command, source)));
    }
}
