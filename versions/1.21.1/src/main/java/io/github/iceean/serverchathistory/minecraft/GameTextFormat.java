package io.github.iceean.serverchathistory.minecraft;
import io.github.iceean.serverchathistory.storage.HistoryEncoding;
import io.github.iceean.serverchathistory.text.ComponentFormat;
public final class GameTextFormat {
    public static String language(net.minecraft.server.level.ServerPlayer player) { return player.clientInformation().language(); }
    private GameTextFormat() { }
    public static final HistoryEncoding CURRENT = new HistoryEncoding("1.21.1", ComponentFormat.MC_1_21_1);
}
