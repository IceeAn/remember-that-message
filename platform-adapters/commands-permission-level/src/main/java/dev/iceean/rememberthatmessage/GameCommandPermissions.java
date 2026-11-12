package dev.iceean.rememberthatmessage;
import dev.iceean.rememberthatmessage.core.AdminPolicy;
import dev.iceean.rememberthatmessage.minecraft.GameProfiles;
import net.minecraft.commands.CommandSourceStack;
final class GameCommandPermissions {
    private GameCommandPermissions() { }
    static boolean allowed(CommandSourceStack source) {
        var player = source.getPlayer();
        boolean op = source.hasPermission(2);
        boolean owner = player != null && GameProfiles.owner(source.getServer(), player);
        return AdminPolicy.allows(player == null && op, player != null, op, owner);
    }
}
