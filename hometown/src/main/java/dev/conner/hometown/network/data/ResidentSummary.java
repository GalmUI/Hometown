package dev.conner.hometown.network.data;

/** Presentation data only: no entity UUID, mutable entity, or NBT crosses the network. */
public record ResidentSummary(String name, String professionKey, boolean child) {
    public static final int MAX_TEXT = 256;
    public ResidentSummary {
        name = bounded(name);
        professionKey = bounded(professionKey);
    }

    public static String bounded(String text) {
        String clean = text.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        if (clean.length() <= MAX_TEXT) return clean;
        int end = MAX_TEXT - 1;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        return clean.substring(0, end) + "…";
    }
}
