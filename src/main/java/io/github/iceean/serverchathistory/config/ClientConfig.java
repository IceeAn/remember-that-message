package io.github.iceean.serverchathistory.config;

import com.google.gson.*;
import io.github.iceean.serverchathistory.core.*;
import io.github.iceean.serverchathistory.storage.JsonSupport;
import java.util.List;
import java.util.Set;

public record ClientConfig(ReplayPreferences preferences, boolean chatPatchesIntegration, boolean skipHistoryNarration) {
    public static final ClientConfig DEFAULT = new ClientConfig(ReplayPreferences.DEFAULT, true, true);
    public static final List<String> KEYS = List.of("receiveHistory", "historyLimit", "events.join", "events.leave",
            "events.death", "events.advancement", "chatPatchesIntegration", "skipHistoryNarration");
    public static ClientConfig read(String text) {
        var root = JsonParser.parseString(text).getAsJsonObject();
        for (var key : root.keySet()) if (!Set.of("receiveHistory", "historyLimit", "events", "chatPatchesIntegration", "skipHistoryNarration").contains(key))
            throw new IllegalArgumentException("Unknown setting: " + key);
        long limit = 0;
        if (root.has("historyLimit")) {
            var value = root.getAsJsonPrimitive("historyLimit");
            if (!value.isNumber()) throw new IllegalArgumentException("Expected number");
            limit = value.getAsBigDecimal().longValueExact();
        }
        int mask = 0;
        var events = root.has("events") ? root.getAsJsonObject("events") : new JsonObject();
        for (var key : events.keySet()) if (!Set.of("join", "leave", "death", "advancement").contains(key))
            throw new IllegalArgumentException("Unknown event: " + key);
        for (var kind : MessageKind.values()) if (kind != MessageKind.CHAT && bool(events, kind.name().toLowerCase(java.util.Locale.ROOT)))
            mask |= ReplayPreferences.bit(kind);
        return new ClientConfig(new ReplayPreferences(bool(root, "receiveHistory"), limit, mask),
                bool(root, "chatPatchesIntegration"), bool(root, "skipHistoryNarration"));
    }
    private static boolean bool(JsonObject object, String key) {
        if (!object.has(key)) return true;
        var value = object.getAsJsonPrimitive(key);
        if (!value.isBoolean()) throw new IllegalArgumentException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
    public String write() {
        var root = new JsonObject(); root.addProperty("receiveHistory", preferences.receiveHistory());
        root.addProperty("historyLimit", preferences.historyLimit());
        var events = new JsonObject();
        for (var kind : MessageKind.values()) if (kind != MessageKind.CHAT)
            events.addProperty(kind.name().toLowerCase(java.util.Locale.ROOT), preferences.includes(kind));
        root.add("events", events); root.addProperty("chatPatchesIntegration", chatPatchesIntegration);
        root.addProperty("skipHistoryNarration", skipHistoryNarration);
        return JsonSupport.GSON.toJson(root) + "\n";
    }
    public String get(String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown setting: " + key);
        var root = JsonParser.parseString(write()).getAsJsonObject();
        return key.startsWith("events.") ? root.getAsJsonObject("events").get(key.substring(7)).getAsString() : root.get(key).getAsString();
    }
    public ClientConfig with(String key, String value) {
        get(key);
        var root = JsonParser.parseString(write()).getAsJsonObject();
        var target = key.startsWith("events.") ? root.getAsJsonObject("events") : root;
        String field = key.startsWith("events.") ? key.substring(7) : key;
        if (key.equals("historyLimit")) target.addProperty(field, Long.parseLong(value));
        else {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true or false");
            target.addProperty(field, Boolean.parseBoolean(value));
        }
        return read(root.toString());
    }
}
