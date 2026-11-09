package io.github.iceean.serverchathistory.network;
import net.minecraft.resources.Identifier;
final class GameIdentifiers {
    private GameIdentifiers() { }
    static Identifier id(String name) { return Identifier.fromNamespaceAndPath("server_chat_history", name); }
}
