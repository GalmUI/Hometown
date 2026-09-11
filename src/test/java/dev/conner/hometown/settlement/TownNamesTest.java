package dev.conner.hometown.settlement;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownNamesTest {
    @Test void trimsAndAllowsUnicodePunctuation() {
        assertEquals("São João — 村!", TownNames.validate("  São João — 村!  "));
        assertEquals("Oakridge", TownNames.validate("\u2003Oakridge\u2003"));
        assertEquals("Oakridge", TownNames.validate("\u00a0Oakridge\u202f"));
        assertThrows(IllegalArgumentException.class, () -> TownNames.validate("\u00a0\u202f"));
    }
    @Test void lengthCountsCodePoints() {
        assertEquals("🌳".repeat(32), TownNames.validate("🌳".repeat(32)));
        assertThrows(IllegalArgumentException.class, () -> TownNames.validate("🌳".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> TownNames.validate(" "));
        assertThrows(IllegalArgumentException.class, () -> TownNames.validate("a".repeat(33)));
        assertEquals("x", TownNames.validate(" x "));
    }
    @Test void rejectsControlFormattingAndMalformedUnicodeEvenAtEdges() {
        for (String value : new String[]{"\nOakridge", "Oak\tRidge", "\u007f", "\u0085", "§aOakridge", "\uD800", "\uDC00"}) {
            assertThrows(IllegalArgumentException.class, () -> TownNames.validate(value));
        }
    }
    @Test void nameUniquenessIsIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(TownNames.key("OAKRIDGE"), TownNames.key("oakridge"));
            assertEquals(TownNames.key("Σ"), TownNames.key("ς"));
        } finally { Locale.setDefault(original); }
    }
}
