package dev.conner.hometown.civic;

import java.util.Locale;
import net.minecraft.world.level.block.entity.SignText;

/** Narrow vanilla-sign grammar for an R3 Animal Farm facility marker. */
public final class AnimalFarmSignGrammar {
    private AnimalFarmSignGrammar() {}

    public static boolean matches(SignText text) {
        if (text == null) return false;
        return matches(text.getMessage(0, false).getString(), text.getMessage(1, false).getString());
    }

    static boolean matches(String line1, String line2) {
        return normalize(line1).equals("[hometown]") && normalize(line2).equals("animal farm");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
