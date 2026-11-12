package dev.iceean.rememberthatmessage.client;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClientAliasesTest {
    @Test void aliasesShareHandlersPermissionsAndProtection() throws Exception {
        var source=ClientCommandTestSupport.source();
        var dispatcher=new CommandDispatcher<FabricClientCommandSource>();
        ClientCommands.registerInto(dispatcher,true);
        var primary=dispatcher.getRoot().getChild("rememberthatmessageclient");
        var alias=dispatcher.getRoot().getChild("rtmclient");
        assertSame(primary,alias.getRedirect());
        assertSame(primary.getRequirement(),alias.getRequirement());
        assertTrue(alias.getRequirement().test(source));
        assertFalse(alias.getRequirement().test(org.mockito.Mockito.mock(FabricClientCommandSource.class)));
        assertSame(primary.getCommand(),alias.getCommand());
        assertTrue(ClientCommandNames.blocks("rtmclient config set receiveHistory false"));
        assertFalse(ClientCommandNames.blocks("rtmclientextra"));
        assertNull(dispatcher.getRoot().getChild("chathistoryclient"));
        assertFalse(dispatcher.parse("rtmclient config get receiveHistory",source).getReader().canRead());
    }
    @Test void disabledOrOccupiedAliasDoesNotProtectOtherCommands() {
        var dispatcher=new CommandDispatcher<FabricClientCommandSource>();
        ClientCommands.registerInto(dispatcher,false);
        assertNull(dispatcher.getRoot().getChild("rtmclient"));
        assertNotNull(dispatcher.getRoot().getChild("rememberthatmessageclient"));
        assertFalse(ClientCommandNames.blocks("rtmclient config set receiveHistory false"));
        var foreign=dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rtmclient").executes(c -> 7));
        ClientCommands.registerInto(dispatcher,true);
        assertSame(foreign,dispatcher.getRoot().getChild("rtmclient"));
        assertNull(foreign.getRedirect());
        assertFalse(ClientCommandNames.blocks("rtmclient"));
    }
}
