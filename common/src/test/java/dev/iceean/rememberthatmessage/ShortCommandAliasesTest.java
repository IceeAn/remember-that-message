package dev.iceean.rememberthatmessage;
import dev.iceean.rememberthatmessage.config.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ShortCommandAliasesTest {
    @TempDir Path dir;
    @Test void defaultsAndRoundTrips() {
        assertTrue(ConfigCodec.read("{}").shortCommandAliases());
        assertTrue(ClientConfig.read("{}").shortCommandAliases());
        assertFalse(ConfigCodec.read(ConfigCodec.write(ConfigCodec.with(ModConfig.defaults(), "shortCommandAliases", "false"))).shortCommandAliases());
        assertFalse(ClientConfig.read(ClientConfig.DEFAULT.with("shortCommandAliases", "false").write()).shortCommandAliases());
        assertThrows(RuntimeException.class, () -> ConfigCodec.read("{\"shortCommandAliases\":1}"));
        assertThrows(RuntimeException.class, () -> ClientConfig.read("{\"shortCommandAliases\":\"false\"}"));
    }
    @Test void startupIsReadOnlyAndErrorsDisableAliases() throws Exception {
        Path file=dir.resolve("settings.json");
        assertTrue(StartupAliases.read(file, false, e -> fail(e)));
        assertFalse(Files.exists(file));
        Files.writeString(file, "{\"shortCommandAliases\":false}");
        assertFalse(StartupAliases.read(file, false, e -> fail(e)));
        assertFalse(StartupAliases.read(file, true, e -> fail(e)));
        Files.writeString(file, "invalid");
        var errors=new java.util.ArrayList<Exception>();
        assertFalse(StartupAliases.read(file, false, errors::add));
        assertFalse(StartupAliases.read(file, true, errors::add));
        assertEquals(2,errors.size());assertEquals("invalid",Files.readString(file));
    }
}
