package dev.iceean.rememberthatmessage.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class ReplaySelection {
    private ReplaySelection() { }
    public static List<HistoryEntry> latest(List<HistoryEntry> history, ReplayPreferences preferences,
                                             Predicate<HistoryEntry> visible) {
        if (!preferences.receiveHistory()) return List.of();
        var selected = new ArrayList<HistoryEntry>();
        for (int i = history.size() - 1; i >= 0; i--) {
            var entry = history.get(i);
            if (preferences.includes(entry.message().kind()) && visible.test(entry)) {
                selected.add(entry);
                if (preferences.historyLimit() != 0 && selected.size() >= preferences.historyLimit()) break;
            }
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }
}
