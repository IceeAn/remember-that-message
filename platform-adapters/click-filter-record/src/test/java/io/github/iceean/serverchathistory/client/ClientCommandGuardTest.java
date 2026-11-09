package io.github.iceean.serverchathistory.client;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClientCommandGuardTest {
    @Test void protectsBothOptionalPrefixForms() {
        for (String command : new String[] {"chathistoryclient", "/chathistoryclient", "chathistoryclient config set receiveHistory false", "/chathistoryclient status"}) {
            assertTrue(ClientCommandGuard.blocks(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))));
            assertTrue(ClientCommandGuard.blocksCommand(command));
        }
    }
    @Test void keepsSuggestionsUnrelatedCommandsAndNativeInvalidCharacters() {
        assertFalse(ClientCommandGuard.blocks(null)); assertFalse(ClientCommandGuard.blocks(Style.EMPTY));
        assertFalse(ClientCommandGuard.blocks(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand("/chathistoryclient"))));
        for (String command : new String[] {"/help", "chathistoryclientother", "//chathistoryclient", "chathistoryclient§ config", "chathistoryclient\nstatus"})
            assertFalse(ClientCommandGuard.blocksCommand(command));
    }
}
