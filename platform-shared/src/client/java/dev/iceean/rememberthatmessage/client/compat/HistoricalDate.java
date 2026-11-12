package dev.iceean.rememberthatmessage.client.compat;

import java.util.Date;

/** Survives Chat Patches' pending-message queue without changing ordinary dates. */
public final class HistoricalDate extends Date {
    private final boolean system;
    public HistoricalDate(long millis, boolean system) { super(millis); this.system = system; }
    public boolean system() { return system; }
}
