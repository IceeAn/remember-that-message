package dev.iceean.rememberthatmessage.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import org.objenesis.ObjenesisStd;
import static org.mockito.Mockito.*;

/** 注册生产命令树，只解析命令；不执行配置写入、界面或反馈动作。 */
public final class ClientCommandTestSupport {
    private static final CommandDispatcher<FabricClientCommandSource> DISPATCHER = createDispatcher();
    private ClientCommandTestSupport() { }

    private static CommandDispatcher<FabricClientCommandSource> createDispatcher() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ClientCommands.register(true);
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher, null);
        return dispatcher;
    }
    public static FabricClientCommandSource source() {
        // 旧 API 没有 attended 方法；公共正对照为提供该方法的新 API 返回 true。
        var source = mock(FabricClientCommandSource.class, invocation ->
                invocation.getMethod().getName().equals("attended") ? true : RETURNS_DEFAULTS.answer(invocation));
        when(source.getPlayer()).thenReturn(new ObjenesisStd().newInstance(LocalPlayer.class));
        return source;
    }
    public static ParseResults<FabricClientCommandSource> parse(String command, FabricClientCommandSource source) {
        ClientCommandNames.dispatcher = DISPATCHER;
        ClientCommandNames.alias = DISPATCHER.getRoot().getChild("rtmclient");
        return DISPATCHER.parse(command, source);
    }
    public static boolean executable(ParseResults<?> parsed) {
        var context = parsed.getContext();
        while (context.getChild() != null) context = context.getChild();
        return !parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && context.getCommand() != null;
    }
}
