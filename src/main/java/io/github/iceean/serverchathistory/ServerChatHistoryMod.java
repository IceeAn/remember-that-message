package io.github.iceean.serverchathistory;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerChatHistoryMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_chat_history");
    private static final Map<MinecraftServer, WorldHistory> WORLDS = new IdentityHashMap<>();

    public static WorldHistory history(MinecraftServer server) { return WORLDS.get(server); }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WORLDS.put(server, new WorldHistory(server,
                FabricLoader.getInstance().getConfigDir().resolve("server_chat_history.json"))));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            var history = WORLDS.remove(server);
            if (history != null) history.close();
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, bound) -> safely(sender.level().getServer(),
                history -> history.record(history.capture().chat(message, sender, bound))));
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> safely(server,
                history -> history.record(history.capture().game(message, overlay))));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> safely(server, history -> history.joined(listener.player)));
        ServerTickEvents.END_SERVER_TICK.register(server -> safely(server, WorldHistory::tick));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> HistoryCommands.register(dispatcher));
    }

    private static void safely(MinecraftServer server, Consumer<WorldHistory> action) {
        var history = WORLDS.get(server);
        if (history == null) return;
        try { action.accept(history); }
        catch (RuntimeException failure) { LOGGER.error("Chat history operation failed", failure); }
    }
}
