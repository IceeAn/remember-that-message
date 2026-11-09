package io.github.iceean.serverchathistory.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ClientCommandGuardTest {
    @Test void clickedCommandsCannotChangeLocalPreferencesButSuggestionsRemainAvailable() {
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistoryclient config set receiveHistory false")));
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, " /chathistoryclient")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.SUGGEST_COMMAND, "/chathistoryclient settings")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistory status")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistoryclient_other")));
        assertFalse(ClientCommandGuard.blocks(Style.EMPTY));
        assertFalse(ClientCommandGuard.blocks(null));
    }
    @ParameterizedTest
    @ValueSource(strings = {"§", "\t", "\n", "\r", "\u0001", "\u007f"})
    void blocksCommandsAfterVanillaRemovesDisallowedCharacters(String removed) {
        String expected = "/chathistoryclient config set receiveHistory false";
        for (String raw : new String[] {
                removed + expected,
                "/chathistory" + removed + "client config set receiveHistory false",
                "/chathistoryclient" + removed + " config set receiveHistory false"
        }) {
            assertEquals(expected, StringUtil.filterText(raw));
            assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, raw)));
        }
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistoryclient" + removed)));
    }
    @Test void normalizationPreservesSuggestionsAndUnrelatedCommands() {
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.SUGGEST_COMMAND,
                "/chathistoryclient§ config set receiveHistory false")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistory§ status")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/chathistoryclient§_other")));
    }
    private static Style click(ClickEvent.Action action, String value) {
        return Style.EMPTY.withClickEvent(new ClickEvent(action, value));
    }
}
