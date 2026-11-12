package dev.iceean.rememberthatmessage.config;

import dev.iceean.rememberthatmessage.storage.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class ConfigManager {
    private final Path path;
    private final Consumer<Exception> errors;
    private ModConfig active = ModConfig.defaults();
    private String error;

    public ConfigManager(Path path, Consumer<Exception> errors) { this.path = path; this.errors = errors; }
    public ModConfig active() { return active; }
    public boolean hasError() { return error != null; }
    public String error() { return error; }

    public boolean reload() {
        try {
            if (!Files.exists(path)) AtomicFiles.write(path, ConfigCodec.write(ModConfig.defaults()), false);
            ModConfig candidate = ConfigCodec.read(Files.readString(path));
            active = candidate;
            error = null;
            return true;
        } catch (Exception failure) {
            error = failure.toString();
            errors.accept(failure);
            return false;
        }
    }

    public void set(String key, String value) throws IOException {
        ModConfig candidate = ConfigCodec.with(active, key, value);
        try { AtomicFiles.write(path, ConfigCodec.write(candidate), true); }
        catch (IOException failure) { error = failure.toString(); errors.accept(failure); throw failure; }
        active = candidate;
        error = null;
    }
}
