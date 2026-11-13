package dev.iceean.rememberthatmessage;

import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentFormatTest {
    @Test void everyOfficialTargetHasAnIndependentNativeIdentity() {
        var versions = List.of("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7",
                "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3");
        var identities = new HashSet<ComponentFormat>();
        for (var version : versions) assertTrue(identities.add(ComponentFormat.forMinecraftVersion(version)), version);
        assertFalse(identities.contains(ComponentFormat.PORTABLE_V1));
        assertEquals(ComponentFormat.values().length - 1, identities.size());
        assertEquals(ComponentFormat.MC_1_20_1, ComponentFormat.forMinecraftVersion("1.20.1"));
        assertEquals(ComponentFormat.MC_1_21_1, ComponentFormat.forMinecraftVersion("1.21.1"));
        assertEquals(ComponentFormat.MC_26_3, ComponentFormat.forMinecraftVersion("26.3"));
    }
    @Test void unknownVersionsAreNotGuessedFromNumericRanges() {
        for (var version : List.of("1.20.7", "1.21.12", "1.21.5-pre1", "26.4", "PORTABLE_V1"))
            assertThrows(IllegalArgumentException.class, () -> ComponentFormat.forMinecraftVersion(version));
        assertFalse(ComponentFormat.MC_1_21_4.modern());
        assertTrue(ComponentFormat.MC_1_21_5.modern());
    }
}
