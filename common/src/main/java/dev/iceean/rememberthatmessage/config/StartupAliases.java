package dev.iceean.rememberthatmessage.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** 启动时只读；错误配置不启用可选短命令。 */
public final class StartupAliases {
    private StartupAliases() { }
    public static boolean read(Path path, boolean client, Consumer<Exception> errors) {
        try {
            if (!Files.exists(path)) return true;
            String json = Files.readString(path);
            return client ? ClientConfig.read(json).shortCommandAliases() : ConfigCodec.read(json).shortCommandAliases();
        } catch (Exception failure) { errors.accept(failure); return false; }
    }
}
