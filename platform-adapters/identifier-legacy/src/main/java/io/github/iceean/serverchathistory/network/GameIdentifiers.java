package io.github.iceean.serverchathistory.network;
import net.minecraft.resources.ResourceLocation;
final class GameIdentifiers {
    private GameIdentifiers() { }
    static ResourceLocation id(String name) { return java.util.Objects.requireNonNull(ResourceLocation.tryParse("server_chat_history:" + name)); }
}
