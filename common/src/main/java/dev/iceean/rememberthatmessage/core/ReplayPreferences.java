package dev.iceean.rememberthatmessage.core;

/** Client preferences never expand the server's recording or visibility policy. */
public record ReplayPreferences(boolean receiveHistory, long historyLimit, int events) {
    public static final int ALL_EVENTS = 15;
    public static final ReplayPreferences DEFAULT = new ReplayPreferences(true, 0, ALL_EVENTS);
    public ReplayPreferences {
        if (historyLimit < 0 || (events & ~ALL_EVENTS) != 0)
            throw new IllegalArgumentException("Invalid replay preferences");
    }
    public boolean includes(MessageKind kind) {
        return kind == MessageKind.CHAT || (events & bit(kind)) != 0;
    }
    public static int bit(MessageKind kind) {
        return switch (kind) { case CHAT -> 0; case JOIN -> 1; case LEAVE -> 2; case DEATH -> 4; case ADVANCEMENT -> 8; };
    }
}
