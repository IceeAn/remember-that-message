package dev.iceean.rememberthatmessage.minecraft;
import net.minecraft.server.level.ServerPlayer;
import static org.mockito.Mockito.*;
public final class GameTestPermissions {
    public static void set(ServerPlayer player, int level) { when(player.hasPermissions(anyInt())).thenAnswer(call -> level >= (int) call.getArgument(0)); }
}
