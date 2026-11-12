package dev.iceean.rememberthatmessage.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AtomicFiles {
    private AtomicFiles() { }
    public static Path backup(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }

    public static void write(Path file, String text, boolean keepBackup) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(text);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (keepBackup && Files.exists(file)) {
                Path copy = Files.createTempFile(file.toAbsolutePath().getParent(), "backup-", ".tmp");
                try {
                    Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    try (FileChannel channel = FileChannel.open(copy, StandardOpenOption.WRITE)) { channel.force(true); }
                    replace(copy, backup(file));
                } finally { Files.deleteIfExists(copy); }
            }
            replace(temporary, file);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}
