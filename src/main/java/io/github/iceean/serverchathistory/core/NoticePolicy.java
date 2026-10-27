package io.github.iceean.serverchathistory.core;

import io.github.iceean.serverchathistory.config.ModConfig;
import java.util.ArrayList;
import java.util.List;

public final class NoticePolicy {
    public enum Notice { START, END, EMPTY, CONFIG_ERROR }
    private NoticePolicy() { }
    public static List<Notice> after(int visibleCount, ModConfig config, boolean configError) {
        List<Notice> result = new ArrayList<>();
        if (visibleCount > 0) result.add(Notice.END);
        else if (config.showEmptyHistory()) result.add(Notice.EMPTY);
        if (config.showConfigErrors() && configError) result.add(Notice.CONFIG_ERROR);
        return List.copyOf(result);
    }
}
