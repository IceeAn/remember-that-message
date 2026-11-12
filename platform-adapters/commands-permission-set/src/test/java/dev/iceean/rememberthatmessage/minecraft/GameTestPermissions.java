package dev.iceean.rememberthatmessage.minecraft;
import net.minecraft.server.level.ServerPlayer;
import static org.mockito.Mockito.*;
public final class GameTestPermissions {
    public static void set(ServerPlayer player, int level) { when(player.permissions()).thenReturn(new net.minecraft.server.permissions.LevelBasedPermissionSet[] { net.minecraft.server.permissions.LevelBasedPermissionSet.ALL, net.minecraft.server.permissions.LevelBasedPermissionSet.MODERATOR, net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER, net.minecraft.server.permissions.LevelBasedPermissionSet.ADMIN, net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER }[level]); }
}
