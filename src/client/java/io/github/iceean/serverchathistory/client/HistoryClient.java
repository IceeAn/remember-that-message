package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.ServerChatHistoryMod;
import io.github.iceean.serverchathistory.client.compat.*;
import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import static io.github.iceean.serverchathistory.client.ClientText.text;

public final class HistoryClient implements ClientModInitializer {
    public static final ClientConfigStore CONFIG = new ClientConfigStore(FabricLoader.getInstance().getConfigDir().resolve("server_chat_history-client.json"));
    private static final HistoryTimeIntegrations INTEGRATIONS = HistoryTimeIntegrations.createDefault();
    private static volatile HistoryProtocol.Status status;
    private static boolean batch;
    private static int remaining;
    private static long lastSequence;
    private static boolean pendingWarning;
    private static boolean warned;
    public static boolean openRequested;

    @Override public void onInitializeClient() {
        CONFIG.reload();
        INTEGRATIONS.onFailure((adapter, failure) -> {
            ServerChatHistoryMod.LOGGER.warn("History time integration {} disabled: {}", adapter.id(), failure);
            pendingWarning = true;
        });
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> reset());
        ClientConfigurationNetworking.registerGlobalReceiver(HistoryProtocol.Hello.TYPE, (payload, context) -> {
            if (ClientConfigurationNetworking.canSend(HistoryProtocol.Preferences.TYPE))
                context.responseSender().sendPacket(new HistoryProtocol.Preferences(HistoryProtocol.VERSION, CONFIG.active().preferences()));
        });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Status.TYPE, (payload, context) -> {
            status = payload;
            if (payload.enhanced() && IntegrationBootstrap.ENABLED && INTEGRATIONS.hasUnavailableIntegration()) pendingWarning = true;
        });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Boundary.TYPE, (payload, context) -> {
            if (payload.start()) { batch = true; remaining = payload.count(); lastSequence = 0; }
            else { batch = false; remaining = 0; }
        });
        ClientPlayNetworking.registerGlobalReceiver(HistoryProtocol.Item.TYPE, (payload, context) -> {
            if (status == null || !status.enhanced() || !batch || remaining <= 0 || payload.sequence() <= lastSequence) {
                ServerChatHistoryMod.LOGGER.warn("Ignored history item outside the negotiated batch"); return;
            }
            remaining--; lastSequence = payload.sequence();
            var client = context.client(); var player = context.player();
            boolean senderRestricted = payload.senderId() != null
                    && (client.isBlocked(payload.senderId()) || client.isFriendOnlyRestricted(payload.senderId()));
            HistoryMessageDisplay.display(payload, player.chatAbilities(), senderRestricted, CONFIG.active().skipHistoryNarration(),
                    insert -> INTEGRATIONS.display(payload, insert),
                    content -> client.gui.hud.getChat().addServerSystemMessage(content),
                    content -> client.getNarrator().saySystemChatQueued(content));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> reset());
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) { openRequested = false; client.gui.setScreen(new HistoryConfigScreen(null)); }
            if (pendingWarning && !warned && client.player != null) {
                pendingWarning = false; warned = true;
                client.gui.hud.getChat().addClientSystemMessage(text("integration_warning"));
            }
        });
        ClientCommands.register();
    }
    private static void reset() {
        status = null; batch = false; remaining = 0; lastSequence = 0; pendingWarning = false; warned = false;
        INTEGRATIONS.resetConnection();
    }
    public static List<Component> statusLines() {
        var lines = new ArrayList<Component>();
        var current = status;
        lines.add(text("connection", text(current == null ? "unknown" : "reason." + current.reason())));
        var saved = CONFIG.active();
        lines.add(text("saved_preferences", saved.preferences().receiveHistory(), limit(saved.preferences().historyLimit()), events(saved.preferences())));
        lines.add(text("narration_status", text(saved.skipHistoryNarration() ? "on" : "off")));
        if (current != null) lines.add(text("active_preferences", current.preferences().receiveHistory(), limit(current.preferences().historyLimit()), events(current.preferences())));
        lines.add(text("integration_switch", saved.messageTimeIntegration(), IntegrationBootstrap.ENABLED));
        for (var adapter : INTEGRATIONS.adapters()) {
            lines.add(text("integration_status", adapter.name(), adapter.version(), text("integration." + adapter.state())));
            if (adapter.failure() != null) lines.add(text("integration_detail", adapter.name(), adapter.failure()));
        }
        if (INTEGRATIONS.hasEnabledIntegration()) lines.add(text("unverified"));
        if (CONFIG.error() != null) lines.add(text("config_error", CONFIG.error()));
        return lines;
    }
    private static Component events(ReplayPreferences preferences) {
        var result = Component.empty();
        for (var kind : MessageKind.values()) if (kind != MessageKind.CHAT && preferences.includes(kind)) {
            if (!result.getSiblings().isEmpty()) result.append(", ");
            result.append(text("setting.events." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        }
        return result.getSiblings().isEmpty() ? text("off") : result;
    }
    private static Object limit(long value) { return value == 0 ? text("follow_server") : value; }
}
