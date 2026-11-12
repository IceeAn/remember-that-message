package dev.iceean.rememberthatmessage.mixin;
import dev.iceean.rememberthatmessage.minecraft.GameTextFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 1.20.1 不保留语言选项，仅缓存客户端已经发送的设置。 */
@Mixin(ServerPlayer.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void rememberThatMessage$language(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        GameTextFormat.language((ServerPlayer)(Object)this, packet.language());
    }
}
