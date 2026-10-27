package io.github.iceean.serverchathistory.storage;

import com.google.gson.*;
import java.time.Instant;

public final class JsonSupport {
    private JsonSupport() { }
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (value, type, context) -> Instant.parse(value.getAsString()))
            .create();
}
