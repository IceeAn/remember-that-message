package io.github.iceean.serverchathistory.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GameNarrator;
import net.minecraft.network.chat.Component;
final class GameNarration {
    private GameNarration() { }
    static void say(Component message) { say(Minecraft.getInstance().getNarrator(), message); }
    static void say(GameNarrator narrator, Component message) { narrator.say(message); }
}
