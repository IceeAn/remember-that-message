package dev.iceean.rememberthatmessage.network;
import net.minecraft.resources.ResourceLocation;
final class GameIdentifiers {
    private GameIdentifiers() { }
    static ResourceLocation id(String name) { return java.util.Objects.requireNonNull(ResourceLocation.tryParse("remember_that_message:" + name)); }
}
