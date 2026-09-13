package dev.conner.hometown.civic;

import java.util.Locale;
import net.minecraft.world.level.block.entity.SignText;

/** Narrow M1 parser for player-owned vanilla civic signs. Lines 3 and 4 are never interpreted. */
public final class TownHallSignGrammar {
    private TownHallSignGrammar() {}

    public static boolean matches(SignText text) {
        return text != null && matches(text.getMessage(0, false).getString(), text.getMessage(1, false).getString());
    }

    public static boolean matches(String firstLine, String secondLine) {
        return "[hometown]".equals(normalize(firstLine)) && "town hall".equals(normalize(secondLine));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
