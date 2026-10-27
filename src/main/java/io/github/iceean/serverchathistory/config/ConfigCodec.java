package io.github.iceean.serverchathistory.config;

import com.google.gson.*;
import io.github.iceean.serverchathistory.storage.JsonSupport;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigCodec {
    public static final List<String> KEYS = List.of("historyLimit", "events.join", "events.leave", "events.death",
            "events.advancement", "replayMode", "showEmptyHistory", "showConfigErrors");
    private ConfigCodec() { }

    public static ModConfig read(String source) {
        JsonObject json = JsonSupport.GSON.fromJson(source, JsonObject.class);
        if (json == null) throw new IllegalArgumentException("Configuration must be an object");
        checkKeys(json, Set.of("historyLimit", "events", "replayMode", "showEmptyHistory", "showConfigErrors"));
        JsonObject events = json.has("events") ? json.getAsJsonObject("events") : new JsonObject();
        if (events == null) throw new IllegalArgumentException("events must be an object");
        checkKeys(events, Set.of("join", "leave", "death", "advancement"));
        long limit = 50;
        if (json.has("historyLimit")) {
            JsonPrimitive value = json.getAsJsonPrimitive("historyLimit");
            if (!value.isNumber()) throw new IllegalArgumentException("historyLimit must be an integer");
            limit = value.getAsBigDecimal().longValueExact();
        }
        String mode = "auto";
        if (json.has("replayMode")) {
            JsonPrimitive value = json.getAsJsonPrimitive("replayMode");
            if (!value.isString()) throw new IllegalArgumentException("replayMode must be a string");
            mode = value.getAsString();
        }
        if (!mode.equals("auto") && !mode.equals("system")) throw new IllegalArgumentException("replayMode must be auto or system");
        return new ModConfig(limit, new ModConfig.Events(bool(events, "join"), bool(events, "leave"),
                bool(events, "death"), bool(events, "advancement")),
                ModConfig.ReplayMode.valueOf(mode.toUpperCase(Locale.ROOT)), bool(json, "showEmptyHistory"), bool(json, "showConfigErrors"));
    }

    private static boolean bool(JsonObject json, String key) {
        if (!json.has(key)) return true;
        JsonPrimitive value = json.getAsJsonPrimitive(key);
        if (!value.isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
        return value.getAsBoolean();
    }

    private static void checkKeys(JsonObject json, Set<String> allowed) {
        for (String key : json.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
    }

    public static JsonObject json(ModConfig config) {
        JsonObject result = JsonSupport.GSON.toJsonTree(config).getAsJsonObject();
        result.addProperty("replayMode", config.replayMode().name().toLowerCase(Locale.ROOT));
        return result;
    }

    public static String write(ModConfig config) { return JsonSupport.GSON.toJson(json(config)) + "\n"; }

    public static String get(ModConfig config, String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
        JsonObject root = json(config);
        return key.startsWith("events.") ? root.getAsJsonObject("events").get(key.substring(7)).getAsString() : root.get(key).getAsString();
    }

    public static ModConfig with(ModConfig config, String key, String value) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
        JsonObject root = json(config);
        JsonObject target = key.startsWith("events.") ? root.getAsJsonObject("events") : root;
        String field = key.startsWith("events.") ? key.substring(7) : key;
        if (key.equals("historyLimit")) target.addProperty(field, Long.parseLong(value));
        else if (key.equals("replayMode")) target.addProperty(field, value);
        else {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true or false");
            target.addProperty(field, Boolean.parseBoolean(value));
        }
        return read(root.toString());
    }
}
