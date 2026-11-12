package dev.iceean.rememberthatmessage.client.compat;

import dev.iceean.rememberthatmessage.config.ClientConfig;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

public final class ChatHeadsBootstrap {
    public static final boolean ENABLED;
    public static String reason;
    static boolean compatible;
    static boolean needsQueueHooks;
    static boolean queueHooks;
    static {
        boolean enabled = false;
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("remember_that_message-client.json");
            enabled = !Files.exists(file) || ClientConfig.read(Files.readString(file)).chatHeadsIntegration();
            reason = enabled ? "checking" : "disabled";
        } catch (Exception error) { reason = "invalid_config"; }
        ENABLED = enabled;
    }
    static boolean available() { return compatible; }
    private ChatHeadsBootstrap() { }
}
