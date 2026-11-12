package dev.iceean.rememberthatmessage.client.compat;

import com.google.common.collect.MapMaker;
import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.ProtocolData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Reuses Chat Heads' own renderer and lookup policy without creating offline profiles. */
public final class ChatHeadsAdapter {
    public static final ChatHeadsAdapter INSTANCE = new ChatHeadsAdapter(ReflectiveAccess::new,
            () -> ChatHeadsBootstrap.available());
    private final Supplier<Access> factory;
    private final Supplier<Boolean> available;
    // MapMaker weak keys use object identity, so equal text from different senders cannot collide.
    private final Map<Component, State> queued = new MapMaker().weakKeys().makeMap();
    private final ThreadLocal<State> current = new ThreadLocal<>();
    private Access access;
    private boolean failed;
    private String failure;
    private Consumer<String> onFailure = ignored -> { };

    ChatHeadsAdapter(Supplier<Access> factory, Supplier<Boolean> available) {
        this.factory = factory; this.available = available;
    }
    public boolean enabled() { return available.get() && !failed; }
    public String state() { return failed ? "runtime_error" : ChatHeadsBootstrap.reason; }
    public String failure() { return failure; }
    public String version() {
        return FabricLoader.getInstance().getModContainer("chat_heads")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
    }
    public void onFailure(Consumer<String> callback) { onFailure = callback; }
    public void resetConnection() { failed = false; failure = null; queued.clear(); current.remove(); }
    private void fail(Throwable error) {
        if (!failed) { failed = true; failure = error.toString(); onFailure.accept(failure); }
    }
    private Access access() { if (access == null) access = factory.get(); return access; }

    public void display(ProtocolData.Item<Component> item, Function<UUID, PlayerInfo> lookup, Consumer<Component> insert) {
        if (!enabled()) { insert.accept(item.content()); return; }
        State previous = null;
        State prepared = null;
        Component content = item.content();
        try {
            previous = access().capture();
            access.clear();
            if (item.kind() == MessageKind.CHAT)
                content = access.decorate(item.content().copy(), lookup.apply(item.senderId()));
            prepared = access.capture();
        } catch (RuntimeException | LinkageError error) {
            fail(error);
        } finally {
            if (previous != null) restore(previous);
        }
        if (prepared == null || failed) { insert.accept(item.content()); return; }
        Component decorated = content;
        inScope(prepared, () -> insert.accept(decorated));
    }

    /** Called only after Chat Patches actually accepts a message into its loading queue. */
    public void captureQueued(Component component) {
        State state = current.get();
        if (state != null && enabled()) queued.put(component, state);
    }
    public void replayQueued(Component component, Runnable insert) {
        State state = queued.remove(component);
        if (state == null || !enabled()) { insert.run(); return; }
        inScope(state, insert);
    }
    private void inScope(State state, Runnable insert) {
        State previous;
        try { previous = access().capture(); }
        catch (RuntimeException | LinkageError error) { fail(error); insert.run(); return; }
        State outer = current.get();
        try {
            try { access.restore(state); current.set(state); }
            catch (RuntimeException | LinkageError error) { fail(error); }
            // No retry or swallowing: insertion may already have produced visible output.
            insert.run();
        } finally {
            if (outer == null) current.remove(); else current.set(outer);
            restore(previous);
        }
    }
    private void restore(State state) {
        try { access.restore(state); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }

    record State(Object last, Object line, Object refreshingLine, boolean refreshing, boolean serverSentUuid) { }
    interface Access {
        State capture();
        void restore(State state);
        void clear();
        Component decorate(Component content, PlayerInfo player);
    }
    private static final class ReflectiveAccess implements Access {
        private final Field last, line, refreshingLine, refreshing, serverSentUuid;
        private final Object empty;
        private final Method decorate;
        ReflectiveAccess() {
            try {
                var loader = getClass().getClassLoader();
                if (ChatHeadsBootstrap.needsQueueHooks) {
                    Class.forName("obro1961.chatpatches.ChatLog", false, loader);
                    if (!ChatHeadsBootstrap.queueHooks) throw new IllegalStateException("Chat Patches queue hooks unavailable");
                }
                var heads = Class.forName("dzwdz.chat_heads.ChatHeads", true, loader);
                var data = Class.forName("dzwdz.chat_heads.HeadData", true, loader);
                last = heads.getField("lastSenderData"); line = heads.getField("lineData");
                refreshingLine = heads.getField("refreshingLineData"); refreshing = heads.getField("refreshing");
                serverSentUuid = heads.getField("serverSentUuid"); empty = data.getField("EMPTY").get(null);
                decorate = GameChatHeads.method(heads);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        public State capture() {
            try { return new State(last.get(null), line.get(null), refreshingLine.get(null), refreshing.getBoolean(null), serverSentUuid.getBoolean(null)); }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        public void restore(State state) {
            try {
                last.set(null, state.last); line.set(null, state.line); refreshingLine.set(null, state.refreshingLine);
                refreshing.setBoolean(null, state.refreshing); serverSentUuid.setBoolean(null, state.serverSentUuid);
            } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        public void clear() {
            try { last.set(null, empty); line.set(null, empty); refreshingLine.set(null, empty); refreshing.setBoolean(null, false); }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        public Component decorate(Component content, PlayerInfo player) {
            try { return GameChatHeads.decorate(decorate, content, player); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    }
}
