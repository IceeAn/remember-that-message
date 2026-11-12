package dev.iceean.rememberthatmessage.client.compat;

import com.google.common.collect.MapMaker;
import dev.iceean.rememberthatmessage.network.ProtocolData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/** Supplies history time only while Chat Tools processes that history entry. */
public final class ChatToolsAdapter implements HistoryTimeAdapter {
    public static final ChatToolsAdapter INSTANCE = new ChatToolsAdapter(
            () -> ChatToolsBootstrap.compatible, ChatToolsAdapter::loadHooks);
    private final BooleanSupplier available;
    private final Runnable initialize;
    private final ThreadLocal<Instant> current = new ThreadLocal<>();
    private final Map<Component, Instant> queued = new MapMaker().weakKeys().makeMap();
    private Consumer<String> onFailure = ignored -> { };
    private boolean initialized;
    private boolean failed;
    private String failure;

    ChatToolsAdapter(BooleanSupplier available, Runnable initialize) {
        this.available = available; this.initialize = initialize;
    }
    public String id() { return "chattools"; }
    public String name() { return "Chat Tools"; }
    public String version() {
        return FabricLoader.getInstance().getModContainer(id())
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
    }
    public String state() { return failed ? "runtime_error" : ChatToolsBootstrap.reason; }
    public String failure() { return failure; }
    public boolean enabled() { return available.getAsBoolean() && !failed; }
    public void onFailure(Consumer<String> callback) { onFailure = callback; }
    public void resetConnection() { failed = false; failure = null; current.remove(); queued.clear(); }
    private boolean ready() {
        if (!enabled()) return false;
        if (initialized) return true;
        try { initialize.run(); initialized = true; return true; }
        catch (RuntimeException | LinkageError error) {
            failed = true; failure = error.toString(); onFailure.accept(failure); return false;
        }
    }
    public void display(ProtocolData.Item<Component> item, Runnable display) {
        if (!ready()) { display.run(); return; }
        inScope(item.time(), display);
    }
    public Instant historyTime() { return enabled() ? current.get() : null; }
    public Instant instantOr(Instant original) {
        var time = historyTime(); return time == null ? original : time;
    }
    public long epochSecondOr(long original) {
        var time = historyTime(); return time == null ? original : time.getEpochSecond();
    }
    public LocalDateTime localTime(ZoneId zone) {
        var time = historyTime(); return time == null ? null : LocalDateTime.ofInstant(time, zone);
    }
    public void captureQueued(Component component) {
        var time = historyTime();
        if (time != null) queued.put(component, time);
    }
    public void replayQueued(Component component, Runnable display) {
        var time = queued.remove(component);
        if (time == null || !ready()) { display.run(); return; }
        inScope(time, display);
    }
    private void inScope(Instant time, Runnable display) {
        var previous = current.get();
        current.set(time);
        try { display.run(); }
        finally { if (previous == null) current.remove(); else current.set(previous); }
    }
    private static void loadHooks() {
        try {
            var loader = ChatToolsAdapter.class.getClassLoader();
            Class.forName("net.apple70cents.chattools.features.general.Timestamp", false, loader);
            Class.forName("net.apple70cents.chattools.utils.TextUtils$MessageUnit", false, loader);
            if (ChatToolsBootstrap.needsQueueHooks) Class.forName("obro1961.chatpatches.ChatLog", false, loader);
            if (!ChatToolsBootstrap.timestampHooks || !ChatToolsBootstrap.messageHooks
                    || ChatToolsBootstrap.needsQueueHooks && !ChatToolsBootstrap.queueHooks)
                throw new IllegalStateException("Chat Tools time hooks unavailable");
        } catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
    }
}
