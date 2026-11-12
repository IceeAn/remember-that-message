package dev.iceean.rememberthatmessage.network;

import java.util.UUID;

/** 每个后端独立的回放窗口；数据包不能重新开启已结束的批次。 */
public final class ClientPlaySession {
    private UUID session, previous;
    private boolean status, enhanced, started, batch, reported;
    private int expected, remaining, unavailable, endCount;
    private long sequence;
    public void reset() { if (session != null) previous = session; session = null; status = enhanced = started = batch = reported = false; expected = remaining = unavailable = endCount = 0; sequence = 0; }
    public record Incomplete(int expected, int received, int unavailable, int endCount) { }
    /** 收到但无法解析的正文或明确占位，不等于用户主动隐藏的消息。 */
    public void unavailable() { if (batch) unavailable++; }
    public Incomplete takeIncomplete() {
        if (!started || batch || reported) return null;
        reported = true;
        return remaining != 0 || endCount != expected || unavailable != 0
                ? new Incomplete(expected, expected - remaining, unavailable, endCount) : null;
    }
    public boolean active() { return session != null; }
    public boolean hello(PortableProtocol.Hello hello) {
        if (active() || hello.session().equals(previous)) return false;
        reset();
        if (hello.version() != PortableProtocol.VERSION) return false;
        session = hello.session(); return true;
    }
    public boolean accept(PortableProtocol.Frame frame) {
        if (session == null || !session.equals(frame.session())) return false;
        if (frame instanceof PortableProtocol.Status value) {
            if (status) return false;
            status = true; enhanced = value.enhanced(); return true;
        }
        if (!status || !enhanced) return false;
        if (frame instanceof PortableProtocol.Boundary value) {
            if (value.start()) {
                if (started) return false;
                started = batch = true; expected = remaining = value.count(); return true;
            }
            if (!batch) return false;
            // 接受边界是为了同步关闭显示层；完整性结果由 takeIncomplete 单独报告。
            batch = false; endCount = value.count(); return true;
        }
        if (frame instanceof PortableProtocol.Item value && batch && remaining > 0 && value.sequence() > sequence) {
            sequence = value.sequence(); remaining--; return true;
        }
        return false;
    }
}
