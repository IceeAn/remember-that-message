package io.github.iceean.serverchathistory.minecraft;
import io.github.iceean.serverchathistory.storage.HistoryEncoding;
import io.github.iceean.serverchathistory.text.ComponentFormat;
public final class GameTextFormat {
    public static String language(net.minecraft.server.level.ServerPlayer player) { return player.clientInformation().language(); }
    private GameTextFormat() { }
    public static final HistoryEncoding CURRENT = GameRuntimeVersion.current();
}
