package dev.iceean.rememberthatmessage.storage;

import com.google.gson.*;
import java.time.Instant;
import java.io.*;
import com.google.gson.stream.*;

public final class JsonSupport {
    private JsonSupport() { }
    public static <T> T read(String json, Class<T> type) {
        if (ENGINE.strict()) return GSON.fromJson(json, type);
        validateTokens(json);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            T value = GSON.getAdapter(type).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new JsonSyntaxException("Trailing JSON content");
            return value;
        } catch (IOException error) { throw new JsonSyntaxException(error); }
    }
    // Gson 2.10's non-lenient reader still permits uppercase literals and raw control
    // characters. Reject those legacy extensions before using its streaming parser.
    private static void validateTokens(String json) {
        boolean string = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (string) {
                if (c < 0x20) throw new JsonSyntaxException("Unescaped control character");
                if (c == '"') string = false;
                else if (c == '\\') {
                    if (++i >= json.length() || "\"\\/bfnrtu".indexOf(json.charAt(i)) < 0)
                        throw new JsonSyntaxException("Invalid JSON escape");
                }
            } else if (c == '"') string = true;
            else if (Character.isLetter(c) && c != 'e' && c != 'E') {
                int start = i;
                while (i + 1 < json.length() && Character.isLetter(json.charAt(i + 1))) i++;
                String token = json.substring(start, i + 1);
                if (!token.equals("true") && !token.equals("false") && !token.equals("null"))
                    throw new JsonSyntaxException("Invalid JSON literal");
            }
        }
    }
    private record Engine(Gson gson, boolean strict) { }
    private static final Engine ENGINE = create();
    public static final Gson GSON = ENGINE.gson();
    private static Engine create() {
        var builder = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (value, type, context) -> Instant.parse(value.getAsString()));
        // Resolve the optional newer Gson API once. Older games retain their bundled Gson.
        try {
            Class<?> strictness = Class.forName("com.google.gson.Strictness");
            builder.getClass().getMethod("setStrictness", strictness).invoke(builder, strictness.getField("STRICT").get(null));
            return new Engine(builder.create(), true);
        } catch (ClassNotFoundException legacyGson) {
            return new Engine(builder.create(), false);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
