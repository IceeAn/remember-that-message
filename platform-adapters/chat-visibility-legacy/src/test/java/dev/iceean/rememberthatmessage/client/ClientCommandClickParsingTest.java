package dev.iceean.rememberthatmessage.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ClientCommandClickParsingTest {
    @ParameterizedTest
    @ValueSource(strings = {" ", "\u2003", "\u2002", "\u3000", "\u00a0", "\u202f", "\t", "\n", "§ ", "\t "})
    void executableClickedConfigurationCommandsAreAlwaysBlocked(String separator) {
        for (String root : new String[]{"rememberthatmessageclient", "rtmclient"}) for (String prefix : new String[]{"/", ""}) {
            String clicked = prefix + root + separator + "config set receiveHistory false";
            String prepared = GameTestCommandClick.prepare(clicked);
            if (prepared == null) continue;
            var parsed = ClientCommandTestSupport.parse(prepared, ClientCommandTestSupport.source());
            if (ClientCommandTestSupport.executable(parsed))
                assertTrue(ClientCommandGuard.blocks(GameTestCommandClick.style(clicked)), clicked);
        }
    }
}
