package dev.iceean.rememberthatmessage.client.compat;

/** Per-mod state; shares only the user's startup time-integration switch. */
final class ChatToolsBootstrap {
    static String reason = IntegrationBootstrap.reason;
    static boolean compatible;
    static boolean needsQueueHooks;
    static boolean timestampHooks;
    static boolean messageHooks;
    static boolean queueHooks;
    private ChatToolsBootstrap() { }
}
