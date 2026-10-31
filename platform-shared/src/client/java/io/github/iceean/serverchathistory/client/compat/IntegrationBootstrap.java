package io.github.iceean.serverchathistory.client.compat;

import io.github.iceean.serverchathistory.config.ClientConfig;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

/** Shared startup switch; must not initialize game or third-party integration classes. */
public final class IntegrationBootstrap {
    public static final boolean ENABLED;
    public static final String reason;
    static {
        boolean enabled = false;
        String startupReason;
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("server_chat_history-client.json");
            enabled = !Files.exists(file) || ClientConfig.read(Files.readString(file)).messageTimeIntegration();
            startupReason = enabled ? "checking" : "disabled";
        } catch (Exception failure) { startupReason = "invalid_config"; }
        ENABLED = enabled;
        reason = startupReason;
    }
    private IntegrationBootstrap() { }
}
