package io.github.iceean.serverchathistory;
import io.github.iceean.serverchathistory.core.AdminPolicy;
import io.github.iceean.serverchathistory.minecraft.GameProfiles;
import net.minecraft.commands.CommandSourceStack;
final class GameCommandPermissions {
    private GameCommandPermissions() { }
    static boolean allowed(CommandSourceStack source) {
        var player = source.getPlayer();
        boolean op = source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        boolean owner = player != null && GameProfiles.owner(source.getServer(), player);
        return AdminPolicy.allows(player == null && op, player != null, op, owner);
    }
}
