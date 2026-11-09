package io.github.iceean.serverchathistory.mixin;

import io.github.iceean.serverchathistory.client.ClientCommandGuard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ClientCommandClickMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void serverChatHistory$requireTypedCommand(Style style, CallbackInfoReturnable<Boolean> result) {
        if (ClientCommandGuard.blocks(style)) result.setReturnValue(true);
    }
}
