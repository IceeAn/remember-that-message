package io.github.iceean.serverchathistory.client.compat;

import io.github.iceean.serverchathistory.config.ClientConfig;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

/** Early bootstrap: no game classes or Chat Patches classes may be initialized here. */
public final class IntegrationBootstrap {
    public static final boolean ENABLED;
    public static String reason;
    public static boolean hooks;
    static {
        boolean enabled = false;
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("server_chat_history-client.json");
            enabled = !Files.exists(file) || ClientConfig.read(Files.readString(file)).chatPatchesIntegration();
            reason = enabled ? "checking" : "disabled";
        } catch (Exception failure) { reason = "invalid_config"; }
        ENABLED = enabled;
    }
    private IntegrationBootstrap() { }
}
