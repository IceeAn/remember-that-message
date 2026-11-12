package dev.iceean.rememberthatmessage.minecraft;
import dev.iceean.rememberthatmessage.storage.HistoryEncoding;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import net.minecraft.SharedConstants;
public final class GameRuntimeVersion {
    private GameRuntimeVersion() { }
    public static HistoryEncoding current() {
        SharedConstants.tryDetectVersion();
        String version = SharedConstants.getCurrentVersion().getName();
        return new HistoryEncoding(version, ComponentFormat.forMinecraftVersion(version));
    }
}
