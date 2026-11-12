package dev.iceean.rememberthatmessage.network;
import net.minecraft.resources.Identifier;
final class GameIdentifiers {
    private GameIdentifiers() { }
    static Identifier id(String name) { return Identifier.fromNamespaceAndPath("remember_that_message", name); }
}
