package io.github.iceean.serverchathistory.config;

import io.github.iceean.serverchathistory.storage.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientConfigStore {
    private final Path path;
    private volatile ClientConfig active = ClientConfig.DEFAULT;
    private String error;
    public ClientConfigStore(Path path) { this.path = path; }
    public ClientConfig active() { return active; }
    public String error() { return error; }
    public boolean reload() {
        try {
            if (!Files.exists(path)) save(active);
            else active = ClientConfig.read(Files.readString(path));
            error = null; return true;
        } catch (Exception failure) { error = failure.toString(); return false; }
    }
    public void save(ClientConfig config) throws IOException {
        AtomicFiles.write(path, config.write(), false);
        active = config; error = null;
    }
}
