package dev.iceean.rememberthatmessage.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps negotiation attached to the same connection across configuration and play. */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface CommonListenerAccessor {
    @Accessor("connection") Connection rememberThatMessage$connection();
}
