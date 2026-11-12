package dev.iceean.rememberthatmessage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerAliasesTest {
    @Test void aliasSharesTreeAndPermission() {
        var d=new CommandDispatcher<CommandSourceStack>();HistoryCommands.register(d,true);
        var p=d.getRoot().getChild("rememberthatmessage");var a=d.getRoot().getChild("rtm");
        assertSame(p,a.getRedirect());assertSame(p.getRequirement(),a.getRequirement());
        assertNull(d.getRoot().getChild("chathistory"));
    }
    @Test void disabledAndOccupiedAliasesLeaveForeignCommandsUntouched() {
        var d=new CommandDispatcher<CommandSourceStack>();HistoryCommands.register(d,false);
        assertNull(d.getRoot().getChild("rtm"));
        var foreign=d.register(LiteralArgumentBuilder.<CommandSourceStack>literal("rtm").executes(c -> 9));
        HistoryCommands.register(d,true);assertSame(foreign,d.getRoot().getChild("rtm"));assertNull(foreign.getRedirect());
    }
}
