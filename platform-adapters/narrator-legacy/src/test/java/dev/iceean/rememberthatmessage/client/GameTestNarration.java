package dev.iceean.rememberthatmessage.client;
import net.minecraft.client.NarratorStatus;
final class GameTestNarration {
    static boolean enabled(NarratorStatus status) { return status.shouldNarrateSystem(); }
}
