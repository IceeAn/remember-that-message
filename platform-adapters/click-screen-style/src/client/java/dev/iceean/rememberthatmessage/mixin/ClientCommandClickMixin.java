package dev.iceean.rememberthatmessage.mixin;

import dev.iceean.rememberthatmessage.client.ClientCommandGuard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ClientCommandClickMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void rememberThatMessage$requireTypedCommand(Style style, CallbackInfoReturnable<Boolean> result) {
        if (ClientCommandGuard.blocks(style)) result.setReturnValue(true);
    }
}
