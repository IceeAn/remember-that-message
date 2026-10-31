package io.github.iceean.serverchathistory.storage;

import io.github.iceean.serverchathistory.core.HistorySnapshot;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.Consumer;

public final class JsonHistoryStore implements HistoryStore {
    private final Path file;
    private final Consumer<HistorySnapshot> validator;
    private final Consumer<Exception> errors;
    private boolean preservationFailed;

    public JsonHistoryStore(Path file, Consumer<HistorySnapshot> validator, Consumer<Exception> errors) {
        this.file = file;
        this.validator = validator;
        this.errors = errors;
    }

    @Override public HistorySnapshot load() {
        HistorySnapshot primary = read(file);
        if (primary != null) return primary;
        HistorySnapshot backup = read(AtomicFiles.backup(file));
        return backup != null ? backup : HistorySnapshot.empty();
    }

    private HistorySnapshot read(Path path) {
        if (!Files.exists(path)) return null;
        try {
            HistorySnapshot result = JsonSupport.read(Files.readString(path), HistorySnapshot.class);
            if (result == null) throw new IOException("Null history snapshot: " + path);
            validator.accept(result);
            return result;
        } catch (Exception failure) {
            errors.accept(failure);
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + UUID.randomUUID()));
            } catch (IOException preserveFailure) {
                preservationFailed = true;
                errors.accept(preserveFailure);
            }
            return null;
        }
    }

    @Override public void save(HistorySnapshot snapshot) throws IOException {
        if (preservationFailed) throw new IOException("Refusing to overwrite history that could not be preserved; repair permissions and restart");
        AtomicFiles.write(file, JsonSupport.GSON.toJson(snapshot) + "\n", true);
    }

    @Override public void reset(HistorySnapshot snapshot) throws IOException {
        String json = JsonSupport.GSON.toJson(snapshot) + "\n";
        AtomicFiles.write(file, json, false);
        AtomicFiles.write(AtomicFiles.backup(file), json, false);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(file.getParent(), file.getFileName() + "*.corrupt-*")) {
            for (Path corrupt : files) Files.delete(corrupt);
        }
        preservationFailed = false;
    }
}
