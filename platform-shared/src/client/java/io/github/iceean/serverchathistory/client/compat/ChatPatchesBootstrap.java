package io.github.iceean.serverchathistory.client.compat;

/** Transformation state belongs only to Chat Patches, not to other time integrations. */
final class ChatPatchesBootstrap {
    static String reason = IntegrationBootstrap.reason;
    static boolean hooks;
    private ChatPatchesBootstrap() { }
}
