package dev.iceean.rememberthatmessage.client;

import dev.iceean.rememberthatmessage.config.ClientConfig;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.iceean.rememberthatmessage.client.ClientText.text;

/** Paged vanilla widgets keep configuration usable without another UI mod. */
public final class HistoryConfigScreen extends Screen {
    private final Screen parent;
    private ClientConfig draft = HistoryClient.CONFIG.active();
    private int page;
    private EditBox limit;
    private Component feedback = text("settings_hint");
    public HistoryConfigScreen(Screen parent) { super(text("title")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(300, width - 24), x = (width - w) / 2;
        addRenderableWidget(new MultiLineTextWidget(width / 2 - w / 2, 10, title, font).setMaxWidth(w).setCentered(true));
        String[] tabs = { "general", "events", "compatibility", "narrator", "status" };
        for (int i = 0; i < tabs.length; i++) {
            int target = i;
            addRenderableWidget(Button.builder(text(tabs[i]), button -> { if (rememberLimit()) page = target; rebuildWidgets(); })
                    .bounds(x + i * (w / tabs.length), 28, w / tabs.length - 2, 20).build());
        }
        limit = null;
        if (page == 0) {
            toggle("receiveHistory", x, 58, w);
            addRenderableWidget(new MultiLineTextWidget(x, 88, text("limit_label"), font).setMaxWidth(w));
            limit = addRenderableWidget(new EditBox(font, x, 106, w, 20, text("limit_label")));
            limit.setMaxLength(19); limit.setValue(draft.get("historyLimit"));
            toggle("shortCommandAliases", x, 132, w);
        } else if (page == 1) {
            String[] keys = { "events.join", "events.leave", "events.death", "events.advancement" };
            for (int i = 0; i < keys.length; i++) toggle(keys[i], x, 56 + i * 24, w);
        } else if (page == 2) {
            toggle("messageTimeIntegration", x, 58, w);
            toggle("chatHeadsIntegration", x, 84, w);
            addRenderableWidget(new MultiLineTextWidget(x, 110, text("heads_hint"), font).setMaxWidth(w).setMaxRows(4));
        } else if (page == 3) {
            toggle("skipHistoryNarration", x, 58, w);
            addRenderableWidget(new MultiLineTextWidget(x, 84, text("narration_hint"), font).setMaxWidth(w).setMaxRows(7));
        } else {
            var message = Component.empty();
            for (var line : HistoryClient.statusLines()) message.append(line).append("\n");
            addRenderableWidget(new MultiLineTextWidget(x, 56, message, font).setMaxWidth(w).setMaxRows(Math.max(3, (height - 116) / 9)));
        }
        if (page != 4) addRenderableWidget(new MultiLineTextWidget(x, 160, feedback, font).setMaxWidth(w).setMaxRows(3));
        int bottom = height - 26;
        addRenderableWidget(Button.builder(text("save"), button -> save()).bounds(x, bottom, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(text("cancel"), button -> onClose()).bounds(x + w / 2, bottom, w / 2, 20).build());
    }
    private void toggle(String key, int x, int y, int w) {
        var button = Button.builder(label(key), pressed -> {
            draft = draft.with(key, Boolean.toString(!Boolean.parseBoolean(draft.get(key)))); pressed.setMessage(label(key));
        }).bounds(x, y, w, 20).build();
        if (key.equals("shortCommandAliases")) button.setTooltip(Tooltip.create(text("aliases_restart")));
        if (key.equals("messageTimeIntegration") || key.equals("chatHeadsIntegration")) button.setTooltip(Tooltip.create(text("restart_hint")));
        addRenderableWidget(button);
    }
    private Component label(String key) { return text("toggle", text("setting." + key), text(Boolean.parseBoolean(draft.get(key)) ? "on" : "off")); }
    private boolean rememberLimit() {
        if (limit == null) return true;
        try { draft = draft.with("historyLimit", limit.getValue()); return true; }
        catch (RuntimeException failure) { feedback = text("invalid_limit"); return false; }
    }
    private void save() {
        if (!rememberLimit()) { rebuildWidgets(); return; }
        try { HistoryClient.CONFIG.save(draft); feedback = text("saved"); }
        catch (Exception failure) { feedback = text("config_error", failure.getMessage()); }
        rebuildWidgets();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
