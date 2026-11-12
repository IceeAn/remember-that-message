package dev.iceean.rememberthatmessage.config;

import dev.iceean.rememberthatmessage.core.MessageKind;
import java.util.Objects;

public record ModConfig(long historyLimit, Events events, ReplayMode replayMode,
                        boolean showEmptyHistory, boolean showConfigErrors, boolean shortCommandAliases) {
    public ModConfig(long historyLimit, Events events, ReplayMode replayMode, boolean showEmptyHistory, boolean showConfigErrors) {
        this(historyLimit, events, replayMode, showEmptyHistory, showConfigErrors, true);
    }
    public enum ReplayMode { AUTO, SYSTEM }
    public record Events(boolean join, boolean leave, boolean death, boolean advancement) { }

    public ModConfig {
        if (historyLimit < 1) throw new IllegalArgumentException("historyLimit must be positive");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(replayMode, "replayMode");
    }

    public static ModConfig defaults() {
        return new ModConfig(50, new Events(true, true, true, true), ReplayMode.AUTO, true, true);
    }

    public boolean includes(MessageKind kind) {
        return switch (kind) {
            case CHAT -> true;
            case JOIN -> events.join();
            case LEAVE -> events.leave();
            case DEATH -> events.death();
            case ADVANCEMENT -> events.advancement();
        };
    }
}
