package dev.conner.hometown.settlement;

import java.util.Locale;

/** Names are limited by Unicode code points, not UTF-16 code units. */
public final class TownNames {
    public static final int MAX_LENGTH = 32;
    private TownNames() {}

    public static String validate(String input) {
        if (input == null || input.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)
                || !hasPairedSurrogates(input)) {
            throw new IllegalArgumentException("hometown.error.name_characters");
        }
        String name = stripWhitespace(input);
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > MAX_LENGTH) throw new IllegalArgumentException("hometown.error.name_length");
        return name;
    }

    private static boolean hasPairedSurrogates(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    private static String stripWhitespace(String value) {
        int start = 0, end = value.length();
        while (start < end && isWhitespace(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && isWhitespace(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    public static String key(String name) {
        // Upper then lower also folds final sigma and sharp-s, independently of server locale.
        return stripWhitespace(name).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }
}
