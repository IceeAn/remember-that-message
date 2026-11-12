package dev.iceean.rememberthatmessage.network;

import dev.iceean.rememberthatmessage.core.ReplayPreferences;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.util.UUID;

/** 所有操作在服务端线程执行；超时后拒绝迟到回复，避免第二次回放。 */
public final class PlaySession {
    private final UUID id = UUID.randomUUID();
    private final long started;
    private boolean offered, done, accepted;
    private String reason = "timeout";
    private ComponentFormat format;
    private ReplayPreferences preferences = ReplayPreferences.DEFAULT;
    public PlaySession(long now) { started = now; }
    public UUID id() { return id; }
    public boolean offer() { if (done || offered) return false; offered = true; return true; }
    public void reply(PortableProtocol.Reply reply) {
        if (done || !offered || !id.equals(reply.session())) return;
        done = true;
        if (reply.version() != PortableProtocol.VERSION || reply.format() == ComponentFormat.PORTABLE_V1) { reason = "version_mismatch"; return; }
        accepted = true; reason = "enhanced"; format = reply.format(); preferences = reply.preferences();
    }
    public boolean ready(long now) { if (!done && now - started >= 2_000_000_000L) done = true; return done; }
    public boolean offered() { return offered; }
    public boolean accepted() { return accepted; }
    public String reason() { return offered ? reason : "vanilla"; }
    public ComponentFormat format() { return format; }
    public ReplayPreferences preferences() { return preferences; }
}
