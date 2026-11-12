package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.ReplayPreferences;

/** Shared by the configuration network thread and task tick; late replies are ignored. */
public final class Negotiation {
    private long started;
    private boolean running;
    private boolean done;
    private ReplayPreferences preferences = ReplayPreferences.DEFAULT;
    private String reason = "timeout";
    public synchronized void start(long now) { started = now; running = true; }
    public synchronized void reply(int version, ReplayPreferences value) {
        if (!running || done) return;
        if (version == ProtocolData.VERSION) { preferences = value; reason = "ok"; }
        else reason = "version_mismatch";
        done = true;
    }
    public synchronized boolean tick(long now) {
        if (running && !done && now - started >= 2_000_000_000L) done = true;
        return done;
    }
    public synchronized boolean accepted() { return done && reason.equals("ok"); }
    public synchronized ReplayPreferences preferences() { return preferences; }
    public synchronized String reason() { return reason; }
}
