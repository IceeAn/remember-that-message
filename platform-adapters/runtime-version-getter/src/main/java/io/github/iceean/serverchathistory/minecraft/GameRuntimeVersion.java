package io.github.iceean.serverchathistory.minecraft;
import io.github.iceean.serverchathistory.storage.HistoryEncoding;
import io.github.iceean.serverchathistory.text.ComponentFormat;
import net.minecraft.SharedConstants;
public final class GameRuntimeVersion {
    private GameRuntimeVersion() { }
    public static HistoryEncoding current() {
        SharedConstants.tryDetectVersion();
        String version = SharedConstants.getCurrentVersion().getName();
        return new HistoryEncoding(version, ComponentFormat.forMinecraftVersion(version));
    }
}
