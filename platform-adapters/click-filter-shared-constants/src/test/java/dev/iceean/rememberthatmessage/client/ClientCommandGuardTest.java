package dev.iceean.rememberthatmessage.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ClientCommandGuardTest {
    @Test void clickedCommandsCannotChangeLocalPreferencesButSuggestionsRemainAvailable() {
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessageclient config set receiveHistory false")));
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, " /rememberthatmessageclient")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.SUGGEST_COMMAND, "/rememberthatmessageclient settings")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessage status")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessageclient_other")));
        assertFalse(ClientCommandGuard.blocks(Style.EMPTY));
        assertFalse(ClientCommandGuard.blocks(null));
    }
    @ParameterizedTest
    @ValueSource(strings = {"§", "\t", "\n", "\r", "\u0001", "\u007f"})
    void blocksCommandsAfterVanillaRemovesDisallowedCharacters(String removed) {
        String expected = "/rememberthatmessageclient config set receiveHistory false";
        for (String raw : new String[] {
                removed + expected,
                "/rememberthatmessage" + removed + "client config set receiveHistory false",
                "/rememberthatmessageclient" + removed + " config set receiveHistory false"
        }) {
            assertEquals(expected, SharedConstants.filterText(raw));
            assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, raw)));
        }
        assertTrue(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessageclient" + removed)));
    }
    @Test void normalizationPreservesSuggestionsAndUnrelatedCommands() {
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.SUGGEST_COMMAND,
                "/rememberthatmessageclient§ config set receiveHistory false")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessage§ status")));
        assertFalse(ClientCommandGuard.blocks(click(ClickEvent.Action.RUN_COMMAND, "/rememberthatmessageclient§_other")));
    }
    private static Style click(ClickEvent.Action action, String value) {
        return Style.EMPTY.withClickEvent(new ClickEvent(action, value));
    }
}
