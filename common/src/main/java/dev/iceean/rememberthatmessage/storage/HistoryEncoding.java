package dev.iceean.rememberthatmessage.storage;

import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.util.Objects;

public record HistoryEncoding(String minecraftVersion, ComponentFormat componentEncoding) {
    public HistoryEncoding {
        Objects.requireNonNull(minecraftVersion); Objects.requireNonNull(componentEncoding);
        if (minecraftVersion.isBlank() || minecraftVersion.length() > 64 || componentEncoding == ComponentFormat.PORTABLE_V1)
            throw new IllegalArgumentException("Invalid native history encoding");
    }
}
