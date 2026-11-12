package dev.iceean.rememberthatmessage.client.compat;

import com.mojang.authlib.GameProfile;
import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.ProtocolData;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public final class ChatPatchesAdapter implements HistoryTimeAdapter {
    private static final Pattern VANILLA = Pattern.compile("^((-> )?\\[.+] )?<[^>]+>\\s.+$", Pattern.DOTALL);
    private Field data;
    private Constructor<?> constructor;
    private boolean failed;
    private String failure;
    private Consumer<String> onFailure = ignored -> { };
    public String id() { return "chatpatches"; }
    public String name() { return "Chat Patches"; }
    public void onFailure(Consumer<String> callback) { onFailure = callback; }
    public void resetConnection() { failed = false; failure = null; }
    public String version() {
        return FabricLoader.getInstance().getModContainer("chatpatches")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
    }
    public String state() { return failed ? "runtime_error" : ChatPatchesBootstrap.reason; }
    public String failure() { return failure; }
    public boolean enabled() { return ChatPatchesBootstrap.hooks && !failed; }
    private void initialize() throws ReflectiveOperationException {
        if (data != null) return;
        var loader = getClass().getClassLoader();
        var util = Class.forName("obro1961.chatpatches.util.ChatUtil", false, loader);
        var type = Class.forName("obro1961.chatpatches.util.ChatUtil$MessageData", false, loader);
        var field = util.getField("messageData");
        var ctor = type.getConstructor(GameProfile.class, Date.class, boolean.class);
        if (field.getType() != type) throw new NoSuchFieldException("messageData type changed");
        constructor = ctor; data = field;
    }
    private void fail(Throwable throwable) {
        failed = true; failure = throwable.toString(); onFailure.accept(failure);
    }
    /** Never retry display after it starts: a downstream exception may occur after insertion. */
    public void display(ProtocolData.Item<Component> item, Runnable display) {
        if (!enabled()) { display.run(); return; }
        Object previous;
        try {
            initialize(); previous = data.get(null);
            boolean chat = item.kind() == MessageKind.CHAT;
            var sender = chat ? new GameProfile(item.senderId(), item.senderName()) : new GameProfile(new UUID(0, 0), "");
            data.set(null, constructor.newInstance(sender, new HistoricalDate(item.time().toEpochMilli(), !chat),
                    chat && VANILLA.matcher(item.content().getString()).matches()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(failure); display.run(); return;
        }
        try { display.run(); }
        catch (RuntimeException | LinkageError failure) { fail(failure); }
        finally {
            try { data.set(null, previous); }
            catch (IllegalAccessException | RuntimeException failure) { fail(failure); }
        }
    }
}
