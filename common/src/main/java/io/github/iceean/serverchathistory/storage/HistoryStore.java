package io.github.iceean.serverchathistory.storage;

import io.github.iceean.serverchathistory.core.HistorySnapshot;
import java.io.IOException;

public interface HistoryStore {
    HistorySnapshot load();
    void save(HistorySnapshot snapshot) throws IOException;
    /** Replaces both recovery copies, including any records created after a clear. */
    void reset(HistorySnapshot snapshot) throws IOException;
}
