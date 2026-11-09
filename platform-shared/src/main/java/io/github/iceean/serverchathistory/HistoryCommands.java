package io.github.iceean.serverchathistory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.iceean.serverchathistory.config.ConfigCodec;
import io.github.iceean.serverchathistory.core.AdminPolicy;
import io.github.iceean.serverchathistory.text.Translations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;


import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HistoryCommands {
    private HistoryCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var key = argument("key", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigCodec.KEYS, builder));
        dispatcher.register(literal("chathistory").requires(HistoryCommands::allowed)
                .then(literal("config").executes(context -> run(context.getSource(), (source, history) -> {
                    for (var name : ConfigCodec.KEYS) reply(source, "config_value", name, ConfigCodec.get(history.config().active(), name));
                }))
                    .then(literal("get").then(key.executes(context -> run(context.getSource(), (source, history) -> {
                        var name = StringArgumentType.getString(context, "key");
                        reply(source, "config_value", name, ConfigCodec.get(history.config().active(), name));
                    }))))
                    .then(literal("set").then(argument("key", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigCodec.KEYS, builder))
                        .then(argument("value", StringArgumentType.word()).executes(context -> run(context.getSource(), (source, history) -> {
                            var name = StringArgumentType.getString(context, "key");
                            var value = StringArgumentType.getString(context, "value");
                            history.config().set(name, value);
                            history.configChanged();
                            reply(source, "config_saved", name, value);
                        }))))))
                .then(literal("reload").executes(context -> run(context.getSource(), (source, history) -> {
                    if (!history.config().reload()) throw new IllegalArgumentException("Configuration reload failed");
                    history.configChanged();
                    reply(source, "config_reloaded");
                })))
                .then(literal("status").executes(context -> run(context.getSource(), (source, history) -> { reply(source, "status",
                        history.size(), history.visibleSize(), history.config().active().historyLimit(),
                        text(source, history.config().active().replayMode() == io.github.iceean.serverchathistory.config.ModConfig.ReplayMode.AUTO ? "auto" : "system"),
                        text(source, history.config().hasError() ? "error" : "healthy"),
                        text(source, history.storageError() ? "error" : "healthy"), history.pendingWrites());
                    var migration = history.migrationReport();
                    if (migration != null) reply(source, "migration_status", migration.sourceMinecraft(), migration.targetMinecraft(), migration.records(), migration.degradedRecords(), String.join(", ", migration.losses()));
                    if (history.migrationFailure() != null) reply(source, "migration_failed", history.migrationFailure());
                })))
                .then(literal("clear").executes(context -> run(context.getSource(), (source, history) -> {
                    reply(source, "clear_pending");
                    history.clear().whenComplete((ignored, failure) -> source.getServer().execute(() -> {
                        if (failure == null) reply(source, "clear_done");
                        else source.sendFailure(Component.literal(text(source, "clear_failed")));
                    }));
                }))));
    }

    static boolean allowed(CommandSourceStack source) { return GameCommandPermissions.allowed(source); }

    @FunctionalInterface private interface Operation { void run(CommandSourceStack source, WorldHistory history) throws Exception; }
    private static int run(CommandSourceStack source, Operation operation) {
        var history = ServerChatHistoryMod.history(source.getServer());
        if (history == null) { source.sendFailure(Component.literal(text(source, "unavailable"))); return 0; }
        try { operation.run(source, history); return 1; }
        catch (Exception failure) {
            ServerChatHistoryMod.LOGGER.warn("Chat history command failed: {}", failure.toString());
            source.sendFailure(Component.literal(text(source, "operation_failed")));
            return 0;
        }
    }

    private static String text(CommandSourceStack source, String key, Object... args) {
        return Translations.text(source.getPlayer() == null ? "en_us" : io.github.iceean.serverchathistory.minecraft.GameTextFormat.language(source.getPlayer()), key, args);
    }
    private static void reply(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.literal(text(source, key, args)), false);
    }
}
