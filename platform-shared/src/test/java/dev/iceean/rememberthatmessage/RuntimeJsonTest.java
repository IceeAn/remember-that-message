package dev.iceean.rememberthatmessage;

import com.google.gson.JsonObject;
import dev.iceean.rememberthatmessage.storage.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeJsonTest {
    @ParameterizedTest
    @ValueSource(strings = {"{key:1}", "{'key':1}", "{/*comment*/\"key\":1}", "{\"key\":TRUE}",
            "{\"key\":NaN}", "{\"key\":\"raw\nnewline\"}", "{\"key\":\"bad\\'escape\"}", "{} {}"})
    void configurationAndStorageRejectNonJsonExtensions(String text) {
        assertThrows(RuntimeException.class, () -> JsonSupport.read(text, JsonObject.class));
    }
    @Test void acceptsStandardEscapesLiteralsAndExponentNumbers() {
        var result = JsonSupport.read("{\"text\":\"你好\\n\\\"quoted\\\"\\u0041\",\"value\":1.25e+2,\"flag\":true,\"empty\":null}", JsonObject.class);
        assertEquals("你好\n\"quoted\"A", result.get("text").getAsString());
        assertEquals(125, result.get("value").getAsInt());
        assertTrue(result.get("flag").getAsBoolean());
        assertTrue(result.get("empty").isJsonNull());
    }
}
