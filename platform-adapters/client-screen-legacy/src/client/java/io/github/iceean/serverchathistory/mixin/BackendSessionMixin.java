package io.github.iceean.serverchathistory.mixin;
import io.github.iceean.serverchathistory.client.HistoryClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class BackendSessionMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void serverChatHistory$backend(ClientboundLoginPacket packet, CallbackInfo ci) { HistoryClient.reset(); }
}
