package dev.conner.hometown.client;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/** Small client-only presentation helper for the fixed vanilla 16-color town palette. */
final class TownColorUi {
    private static final DyeColor[] COLORS = DyeColor.values();
    private TownColorUi() {}

    static DyeColor step(DyeColor current, int delta) {
        int next = Math.floorMod(current.ordinal() + delta, COLORS.length);
        return COLORS[next];
    }

    static Component label(DyeColor color) {
        String raw = color.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (upper && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
            }
            if (c == ' ') upper = true;
        }
        return Component.literal(result.toString());
    }

    static int rgb(DyeColor color) {
        return switch (color) {
            case WHITE -> 0xF9FFFE;
            case ORANGE -> 0xF9801D;
            case MAGENTA -> 0xC74EBD;
            case LIGHT_BLUE -> 0x3AB3DA;
            case YELLOW -> 0xFED83D;
            case LIME -> 0x80C71F;
            case PINK -> 0xF38BAA;
            case GRAY -> 0x474F52;
            case LIGHT_GRAY -> 0x9D9D97;
            case CYAN -> 0x169C9C;
            case PURPLE -> 0x8932B8;
            case BLUE -> 0x3C44AA;
            case BROWN -> 0x835432;
            case GREEN -> 0x5E7C16;
            case RED -> 0xB02E26;
            case BLACK -> 0x1D1D21;
        };
    }
}
