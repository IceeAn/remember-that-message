package dev.iceean.rememberthatmessage.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.iceean.rememberthatmessage.config.ClientConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static dev.iceean.rememberthatmessage.client.ClientText.text;

public final class ClientCommands {
    private ClientCommands() { }
    public static void register() {
        register(dev.iceean.rememberthatmessage.config.StartupAliases.read(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("remember_that_message-client.json"), true,
                failure -> dev.iceean.rememberthatmessage.RememberThatMessageMod.LOGGER.warn("Short client aliases disabled: {}", failure.toString())));
    }
    public static void register(boolean aliases) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerInto(dispatcher, aliases));
    }
    static void registerInto(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher, boolean aliases) {
            ClientCommandNames.startupEnabled = aliases;
            ClientCommandNames.alias = null;
            var primary = dispatcher.register(
                literal("rememberthatmessageclient").requires(source -> source.getPlayer() != null)
                .executes(ctx -> { HistoryClient.openRequested = true; return 1; })
                .then(literal("status").executes(ctx -> {
                    HistoryClient.statusLines().forEach(ctx.getSource()::sendFeedback); return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    if (!HistoryClient.CONFIG.reload()) { ctx.getSource().sendError(text("config_error", HistoryClient.CONFIG.error())); return 0; }
                    ctx.getSource().sendFeedback(text("saved")); return 1;
                }))
                .then(literal("config")
                    .then(literal("get").then(argument("key", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ClientConfig.KEYS, builder))
                        .executes(ctx -> execute(ctx.getSource(), () -> {
                            String key = StringArgumentType.getString(ctx, "key");
                            ctx.getSource().sendFeedback(Component.literal(key + " = " + HistoryClient.CONFIG.active().get(key)));
                        }))))
                    .then(literal("set").then(argument("key", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ClientConfig.KEYS, builder))
                        .then(argument("value", StringArgumentType.word()).executes(ctx -> execute(ctx.getSource(), () -> {
                            HistoryClient.CONFIG.save(HistoryClient.CONFIG.active().with(StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "value")));
                            ctx.getSource().sendFeedback(text("saved"));
                        })))))));
            if (aliases) {
                if (dispatcher.getRoot().getChild("rtmclient") == null) {
                    ClientCommandNames.alias = dispatcher.register(literal("rtmclient")
                            .requires(primary.getRequirement()).executes(primary.getCommand()).redirect(primary));
                } else dev.iceean.rememberthatmessage.RememberThatMessageMod.LOGGER.warn("Short command /rtmclient already exists; alias skipped");
            }
            ClientCommandNames.dispatcher = dispatcher;
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static int execute(FabricClientCommandSource source, Action action) {
        try { action.run(); return 1; }
        catch (Exception failure) { source.sendError(text("config_error", failure.getMessage())); return 0; }
    }
}
