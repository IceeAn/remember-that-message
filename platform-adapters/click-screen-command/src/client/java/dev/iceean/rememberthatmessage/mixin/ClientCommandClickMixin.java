package dev.iceean.rememberthatmessage.mixin;
import dev.iceean.rememberthatmessage.client.ClientCommandGuard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class ClientCommandClickMixin {
    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void rememberThatMessage$requireTypedCommand(LocalPlayer player, String command, Screen screen, CallbackInfo result) {
        if (ClientCommandGuard.blocksCommand(command)) result.cancel();
    }
}
