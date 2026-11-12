package dev.iceean.rememberthatmessage.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ClientCommandParsingTest {
    @Test void ordinarySpaceReachesTheProductionConfigurationCommand() {
        String command = "rememberthatmessageclient config set receiveHistory false";
        var parsed = ClientCommandTestSupport.parse(command, ClientCommandTestSupport.source());
        assertTrue(ClientCommandTestSupport.executable(parsed));
        var context = parsed.getContext().build(command);
        assertEquals("receiveHistory", context.getArgument("key", String.class));
        assertEquals("false", context.getArgument("value", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u2003", "\u2002", "\u3000", "\u00a0", "\u202f", "\t", "\n"})
    void unicodeWhitespaceIsNotALiteralCommandBoundary(String separator) {
        String command = "rememberthatmessageclient" + separator + "config set receiveHistory false";
        var parsed = ClientCommandTestSupport.parse(command, ClientCommandTestSupport.source());
        assertFalse(ClientCommandTestSupport.executable(parsed));
        assertTrue(parsed.getReader().canRead());
        assertNull(parsed.getContext().getCommand());
    }
}
