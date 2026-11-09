package io.github.iceean.serverchathistory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.iceean.serverchathistory.client.compat.ChatHeadsAdapter;
import net.minecraft.client.gui.components.ChatComponent;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "obro1961.chatpatches.ChatLog", remap = false)
public abstract class ChatPatchesHeadsQueueMixin {
    @Inject(method = "queueMessage", at = @At("RETURN"), remap = false)
    private static void serverChatHistory$captureHead(Component content, MessageSignature signature,
            GuiMessageTag tag, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) ChatHeadsAdapter.INSTANCE.captureQueued(content);
    }

    @WrapOperation(method = "drainPendingMessages", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", remap = true), remap = false)
    private static void serverChatHistory$restoreHead(ChatComponent chat, Component content, MessageSignature signature,
            GuiMessageTag tag, Operation<Void> original) {
        ChatHeadsAdapter.INSTANCE.replayQueued(content, () -> original.call(chat, content, signature, tag));
    }
}
