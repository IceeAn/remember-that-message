package io.github.iceean.serverchathistory.text;

import com.google.gson.reflect.TypeToken;
import io.github.iceean.serverchathistory.storage.JsonSupport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Translations {
    private static final Map<String, String> ENGLISH = load("en_us");
    private static final Map<String, String> CHINESE = load("zh_cn");
    private Translations() { }

    private static Map<String, String> load(String language) {
        String resource = "/assets/server_chat_history/lang/" + language + ".json";
        try (InputStream input = Translations.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing translation resource: " + resource);
            return Map.copyOf(JsonSupport.GSON.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() { }.getType()));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    public static String text(String language, String key, Object... values) {
        String result = ("zh_cn".equals(language) ? CHINESE : ENGLISH).getOrDefault(key, key);
        for (int i = 0; i < values.length; i++) result = result.replace("{" + i + "}", String.valueOf(values[i]));
        return result;
    }
}
