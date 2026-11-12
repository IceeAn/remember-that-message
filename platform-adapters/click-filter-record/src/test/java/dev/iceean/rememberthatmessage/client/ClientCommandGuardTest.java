package dev.iceean.rememberthatmessage.client;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClientCommandGuardTest {
    @Test void protectsBothOptionalPrefixForms() {
        for (String command : new String[] {"rememberthatmessageclient", "/rememberthatmessageclient", "rememberthatmessageclient config set receiveHistory false", "/rememberthatmessageclient status"}) {
            assertTrue(ClientCommandGuard.blocks(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))));
            assertTrue(ClientCommandGuard.blocksCommand(command));
        }
    }
    @Test void keepsSuggestionsUnrelatedCommandsAndNativeInvalidCharacters() {
        assertFalse(ClientCommandGuard.blocks(null)); assertFalse(ClientCommandGuard.blocks(Style.EMPTY));
        assertFalse(ClientCommandGuard.blocks(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand("/rememberthatmessageclient"))));
        for (String command : new String[] {"/help", "rememberthatmessageclientother", "//rememberthatmessageclient", "rememberthatmessageclient§ config", "rememberthatmessageclient\nstatus"})
            assertFalse(ClientCommandGuard.blocksCommand(command));
    }
}
