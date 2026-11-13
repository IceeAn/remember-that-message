package dev.iceean.rememberthatmessage.client.mixin;

import dev.iceean.rememberthatmessage.client.compat.HistoricalDate;

import java.util.Date;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "obro1961.chatpatches.config.Config", remap = false)
public abstract class ChatPatchesTimeMixin {
    @ModifyVariable(method = "makeTimestamp(Ljava/util/Date;ZZ)Lnet/minecraft/network/chat/MutableComponent;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private boolean rememberThatMessage$eventType(boolean original, Date when, boolean system, boolean boundary) {
        return when instanceof HistoricalDate historical ? historical.system() : original;
    }
}
