package dev.iceean.rememberthatmessage.minecraft;
import dev.iceean.rememberthatmessage.storage.HistoryEncoding;
public final class GameTextFormat {
    public static void register() { }
    public static String language(net.minecraft.server.level.ServerPlayer player) { return player.clientInformation().language(); }
    private GameTextFormat() { }
    public static final HistoryEncoding CURRENT = GameRuntimeVersion.current();
}
