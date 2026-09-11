package dev.conner.hometown.housing;

/** Descriptive density only; never changes structural capacity. */
public enum RoomDensity {
    PRIVATE(100), SHARED(80), CROWDED(60), HIGH_DENSITY(40);
    private final int privacyPoints;
    RoomDensity(int privacyPoints) { this.privacyPoints = privacyPoints; }
    public int privacyPoints() { return privacyPoints; }
    public static RoomDensity forBeds(int beds) {
        if (beds < 1) throw new IllegalArgumentException("An enclosed Housing room must contain a bed");
        return beds == 1 ? PRIVATE : beds == 2 ? SHARED : beds <= 4 ? CROWDED : HIGH_DENSITY;
    }
}
