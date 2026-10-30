package io.github.iceean.serverchathistory.client.mixin;

import io.github.iceean.serverchathistory.client.compat.ChatToolsAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.apple70cents.chattools.utils.TextUtils$MessageUnit", remap = false)
public abstract class ChatToolsMessageTimeMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;JIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static long serverChatHistory$copyTime(long original) {
        return ChatToolsAdapter.INSTANCE.epochSecondOr(original);
    }
}
