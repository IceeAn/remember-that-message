package dev.iceean.rememberthatmessage.storage;

import com.google.gson.*;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.text.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public final class JsonHistoryStore implements HistoryStore {
    private final Path file;
    private final HistoryEncoding target;
    private final Consumer<HistorySnapshot> validator;
    private final Consumer<Exception> errors;
    private boolean preservationFailed;
    private String blocked;
    private MigrationReport migration;
    public record MigrationReport(String sourceMinecraft, String targetMinecraft, int records, int degradedRecords, Set<String> losses) {
        public MigrationReport { losses = Set.copyOf(losses); }
    }
    private static final class Incompatible extends Exception {
        Incompatible(String message) { super(message); }
        Incompatible(String message, Throwable cause) { super(message, cause); }
    }

    public JsonHistoryStore(Path file, HistoryEncoding target, Consumer<HistorySnapshot> validator, Consumer<Exception> errors) {
        this.file = file; this.target = target; this.validator = validator; this.errors = errors;
    }
    public Path migrationBackup() { return file.resolveSibling(file.getFileName() + ".previous-version"); }
    public MigrationReport migrationReport() { return migration; }
    public String blockedReason() { return blocked; }

    @Override public HistorySnapshot load() {
        HistorySnapshot primary = read(file);
        if (primary != null) return primary;
        if (blocked != null) return HistorySnapshot.empty();
        HistorySnapshot backup = read(AtomicFiles.backup(file));
        return backup != null ? backup : HistorySnapshot.empty();
    }
    private HistorySnapshot read(Path path) {
        if (!Files.exists(path)) return null;
        try {
            String original = Files.readString(path);
            JsonObject root = JsonSupport.read(original, JsonObject.class);
            if (root == null) throw new IOException("Null history snapshot: " + path);
            if (!root.has("version")) throw new Incompatible("Missing history format version");
            int version = root.get("version").getAsBigDecimal().intValueExact();
            if (version != 2 && version != HistoryFile.VERSION) throw new Incompatible("Unsupported history format: " + version);
            HistorySnapshot snapshot; ComponentFormat source = null; String sourceMinecraft = "unmarked";
            if (version == 2) snapshot = JsonSupport.GSON.fromJson(root, HistorySnapshot.class);
            else {
                if (!root.has("minecraftVersion") || !root.has("componentEncoding")) throw new Incompatible("Missing history encoding metadata");
                try { source = ComponentFormat.valueOf(root.get("componentEncoding").getAsString()); }
                catch (RuntimeException failure) { throw new Incompatible("Unknown component encoding", failure); }
                if (source == ComponentFormat.PORTABLE_V1) throw new Incompatible("Disk history must use native components");
                var stored = JsonSupport.GSON.fromJson(root, HistoryFile.class);
                sourceMinecraft = stored.minecraftVersion(); snapshot = stored.snapshot();
            }
            if (source == target.componentEncoding()) {
                validator.accept(snapshot);
                return snapshot;
            }
            // 从本行开始失败属于迁移失败；保留原件，不自动恢复旧副本后覆盖它。
            try {
                var converted = new ArrayList<HistoryEntry>(snapshot.entries().size());
                var losses = new LinkedHashSet<String>(); int degraded = 0;
                for (var entry : snapshot.entries()) {
                    var message = entry.message(); var recordLoss = new LinkedHashSet<String>();
                    String rendered = convert(message.renderedJson(), source, recordLoss);
                    var chat = message.chat();
                    if (chat != null && chat.filteredJson() != null) chat = new HistoryEntry.Chat(chat.senderId(), chat.senderName(),
                            chat.senderFiltering(), chat.filtering(), convert(chat.filteredJson(), source, recordLoss));
                    if (!recordLoss.isEmpty()) degraded++;
                    losses.addAll(recordLoss);
                    converted.add(new HistoryEntry(entry.sequence(), new HistoryEntry.Message(message.kind(), message.time(), rendered, chat)));
                }
                var result = new HistorySnapshot(HistorySnapshot.VERSION, snapshot.nextSequence(), converted);
                validator.accept(result);
                String output = serialize(result);
                // 固定单份迁移备份；日常 .bak 必须随后使用新格式，不能留下第二份旧版本备份。
                AtomicFiles.write(migrationBackup(), original, false);
                AtomicFiles.write(file, output, false);
                AtomicFiles.write(AtomicFiles.backup(file), output, false);
                migration = new MigrationReport(sourceMinecraft, target.minecraftVersion(), converted.size(), degraded, losses);
                return result;
            } catch (Exception failure) { throw new Incompatible("History migration stopped; original retained in source or .previous-version", failure); }
        } catch (Incompatible failure) {
            blocked = failure.toString() + (failure.getCause() == null ? "" : ": " + failure.getCause());
            errors.accept(failure); return null;
        } catch (Exception failure) {
            errors.accept(failure);
            try { Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + UUID.randomUUID())); }
            catch (IOException preserveFailure) { preservationFailed = true; errors.accept(preserveFailure); }
            return null;
        }
    }
    private String convert(String json, ComponentFormat source, Set<String> losses) {
        ComponentFormat actual = source == null ? TextConversion.legacyFormat(json, target.componentEncoding()) : source;
        var result = TextConversion.convert(json, actual, target.componentEncoding());
        losses.addAll(result.losses()); return result.json();
    }
    private String serialize(HistorySnapshot snapshot) {
        return JsonSupport.GSON.toJson(HistoryFile.of(snapshot, target)) + "\n";
    }
    @Override public void save(HistorySnapshot snapshot) throws IOException {
        if (blocked != null || preservationFailed) throw new IOException("Refusing to overwrite preserved history: " + blocked);
        AtomicFiles.write(file, serialize(snapshot), true);
    }
    @Override public void reset(HistorySnapshot snapshot) throws IOException {
        String json = serialize(snapshot);
        AtomicFiles.write(file, json, false);
        AtomicFiles.write(AtomicFiles.backup(file), json, false);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(file.getParent(), file.getFileName() + "*.corrupt-*")) {
            for (Path corrupt : files) Files.delete(corrupt);
        }
        Files.deleteIfExists(migrationBackup());
        preservationFailed = false; blocked = null; migration = null;
    }
}
