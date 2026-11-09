package io.github.iceean.serverchathistory.client;

import io.github.iceean.serverchathistory.ServerChatHistoryMod;
import io.github.iceean.serverchathistory.client.compat.*;
import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.network.*;
import io.github.iceean.serverchathistory.minecraft.*;
import io.github.iceean.serverchathistory.text.*;
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
    private static final ChatHeadsAdapter HEADS = ChatHeadsAdapter.INSTANCE;
    private static volatile HistoryProtocol.Status status;
    private static final ClientPlaySession PORTABLE = new ClientPlaySession();
    private static boolean batch;
    private static int remaining;
    private static long lastSequence;
    private static boolean pendingWarning;
    private static boolean warned;
    private static boolean pendingConversionWarning, conversionWarned;
    public static boolean openRequested;

    @Override public void onInitializeClient() {
        CONFIG.reload();
        INTEGRATIONS.onFailure((adapter, failure) -> {
            ServerChatHistoryMod.LOGGER.warn("History time integration {} disabled: {}", adapter.id(), failure);
            pendingWarning = true;
        });
        HEADS.onFailure(failure -> {
            ServerChatHistoryMod.LOGGER.warn("Chat Heads history integration disabled: {}", failure);
            pendingWarning = true;
        });
        GameClientNetworking.register();
        GameClientNetworking.registerLegacy();
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) { openRequested = false; GameClientPlatform.openSettings(); }
            if (client.player != null) {
                var incomplete = PORTABLE.takeIncomplete();
                if (incomplete != null) {
                    pendingConversionWarning = false; conversionWarned = true;
                    GameClientPlatform.localMessage(text("incomplete_history"));
                    ServerChatHistoryMod.LOGGER.warn("Incomplete history replay: expected={}, received={}, unavailable={}, end={}",
                            incomplete.expected(), incomplete.received(), incomplete.unavailable(), incomplete.endCount());
                }
            }
            if (pendingConversionWarning && !conversionWarned && client.player != null) {
                pendingConversionWarning = false; conversionWarned = true;
                GameClientPlatform.localMessage(text("conversion_warning"));
            }
            if (pendingWarning && !warned && client.player != null) {
                pendingWarning = false; warned = true;
                GameClientPlatform.localMessage(text("integration_warning"));
            }
        });
        ClientCommands.register();
    }
    static boolean portableActive() { return PORTABLE.active(); }
    public static void reset() {
        PORTABLE.reset(); pendingConversionWarning = false; conversionWarned = false;
        status = null; batch = false; remaining = 0; lastSequence = 0; pendingWarning = false; warned = false;
        INTEGRATIONS.resetConnection();
        HEADS.resetConnection();
    }
    static void acceptStatus(HistoryProtocol.Status payload) {
        status = payload;
        if (payload.enhanced() && IntegrationBootstrap.ENABLED && INTEGRATIONS.hasUnavailableIntegration()) pendingWarning = true;
        if (payload.enhanced() && ChatHeadsBootstrap.ENABLED && !HEADS.enabled() && !HEADS.state().equals("absent")) pendingWarning = true;
    }
    static void boundary(boolean start, int count) {
        batch = start; remaining = start ? count : 0;
        if (start) lastSequence = 0;
    }
    private static boolean acceptSequence(long sequence) {
        if (status == null || !status.enhanced() || !batch || remaining <= 0 || sequence <= lastSequence) return false;
        remaining--; lastSequence = sequence;
        return true;
    }
    static void acceptItem(HistoryProtocol.Item payload) {
        if (acceptSequence(payload.sequence())) displayItem(payload, false);
    }
    private static boolean canReceive(MessageKind kind, java.util.UUID senderId) {
        return GameClientPlatform.canReceive(kind, senderId);
    }
    private static void displayItem(HistoryProtocol.Item payload, boolean placeholder) {
        GameClientPlatform.display(payload, PortableItemDecoder.presentation(payload, placeholder), INTEGRATIONS, HEADS);
    }
    public static void receivePortable(byte[] bytes) {
        try {
            var frame = PortableProtocol.decode(bytes);
            if (frame instanceof PortableProtocol.Hello hello) {
                if (PORTABLE.active()) return;
                reset();
                if (PORTABLE.hello(hello)) GameClientNetworking.send(PortableProtocol.encode(new PortableProtocol.Reply(
                        hello.session(), PortableProtocol.VERSION, GameTextFormat.CURRENT.componentEncoding(), CONFIG.active().preferences())));
                return;
            }
            if (!PORTABLE.accept(frame)) return;
            if (frame instanceof PortableProtocol.Status value) acceptStatus(new HistoryProtocol.Status(true, value.enhanced(), value.preferences(), value.reason()));
            else if (frame instanceof PortableProtocol.Boundary value) boundary(value.start(), value.count());
            else if (frame instanceof PortableProtocol.Item value) {
                if (!acceptSequence(value.sequence())) return;
                PortableItemDecoder.receive(value, PORTABLE, canReceive(value.kind(), value.senderId()), () -> {
                    var client = Minecraft.getInstance();
                    if (client.level == null) throw new IllegalStateException("Missing client world during history replay");
                    return new MessageCodec(client.level.registryAccess());
                }, decoded -> {
                    displayItem(decoded.item(), decoded.placeholder());
                    if (decoded.simplified()) pendingConversionWarning = true;
                });
            }
        } catch (RuntimeException failure) {
            ServerChatHistoryMod.LOGGER.warn("Ignored invalid portable history: {}", failure.toString());
        }
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
        lines.add(text("heads_switch", saved.chatHeadsIntegration(), ChatHeadsBootstrap.ENABLED));
        lines.add(text("integration_status", "Chat Heads", HEADS.version(), text("integration." + HEADS.state())));
        if (HEADS.failure() != null) lines.add(text("integration_detail", "Chat Heads", HEADS.failure()));
        if (INTEGRATIONS.hasEnabledIntegration() || HEADS.enabled()) lines.add(text("unverified"));
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
