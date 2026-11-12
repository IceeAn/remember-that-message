package dev.iceean.rememberthatmessage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.iceean.rememberthatmessage.client.compat.ChatToolsAdapter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.apple70cents.chattools.features.general.Timestamp", remap = false)
public abstract class ChatToolsTimestampMixin {
    @Shadow(remap = false) public static String timeInFormat(String pattern, LocalDateTime time) { throw new AssertionError(); }

    @ModifyExpressionValue(method = "work", at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;"), remap = false)
    private static Instant rememberThatMessage$hoverTime(Instant original) {
        return ChatToolsAdapter.INSTANCE.instantOr(original);
    }
    @WrapOperation(method = "work", at = @At(value = "INVOKE",
            target = "Lnet/apple70cents/chattools/features/general/Timestamp;timeInFormat(Ljava/lang/String;)Ljava/lang/String;"), remap = false)
    private static String rememberThatMessage$prefixTime(String pattern, Operation<String> original) {
        var time = ChatToolsAdapter.INSTANCE.localTime(ZoneId.systemDefault());
        return time == null ? original.call(pattern) : timeInFormat(pattern, time);
    }
}
