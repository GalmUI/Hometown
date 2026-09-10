package dev.conner.hometown.housing;

import java.util.OptionalInt;

/** Derived counts only. When incomplete, observations are partial and capacity is unavailable. */
public record HousingSnapshot(int population, int totalBeds, int enclosedBeds, int unsealedBeds,
        int roomCount, int privateRooms, int sharedRooms, int sharedBeds, int unknownBeds, boolean scanComplete,
        int crowdedRooms, int highDensityRooms, int privateBeds, int crowdedBeds, int highDensityBeds, OptionalInt privacyPercent) {
    public enum HousingCapacityState { NO_RESIDENTS, SHORTAGE, SUFFICIENT, SCAN_INCOMPLETE }
    /** Convenience for snapshots containing only private and two-bed shared rooms. */
    public HousingSnapshot(int population, int totalBeds, int enclosedBeds, int unsealedBeds,
            int roomCount, int privateRooms, int sharedRooms, int sharedBeds, int unknownBeds, boolean scanComplete) {
        this(population,totalBeds,enclosedBeds,unsealedBeds,roomCount,privateRooms,sharedRooms,sharedBeds,unknownBeds,scanComplete,
                0,0,enclosedBeds-sharedBeds,0,0,privacy(scanComplete,enclosedBeds,enclosedBeds-sharedBeds,sharedBeds,0,0));
    }
    /** Invoked while aggregating on the server. The packet carries this value to the renderer. */
    public static OptionalInt privacy(boolean complete, int enclosed, int privateBeds, int sharedBeds, int crowdedBeds, int highDensityBeds) {
        if (!complete || enclosed == 0) return OptionalInt.empty();
        long points = (long)privateBeds * RoomDensity.PRIVATE.privacyPoints() + (long)sharedBeds * RoomDensity.SHARED.privacyPoints()
                + (long)crowdedBeds * RoomDensity.CROWDED.privacyPoints() + (long)highDensityBeds * RoomDensity.HIGH_DENSITY.privacyPoints();
        return OptionalInt.of((int)((points + enclosed / 2) / enclosed));
    }
    public HousingSnapshot {
        java.util.Objects.requireNonNull(privacyPercent);

        if (population < 0 || totalBeds < 0 || enclosedBeds < 0 || unsealedBeds < 0 || roomCount < 0
                || privateRooms < 0 || sharedRooms < 0 || sharedBeds < 0 || unknownBeds < 0
                || (long)enclosedBeds + unsealedBeds + unknownBeds != totalBeds
                || crowdedRooms < 0 || highDensityRooms < 0 || privateBeds < 0 || crowdedBeds < 0 || highDensityBeds < 0
                || (long)privateRooms + sharedRooms + crowdedRooms + highDensityRooms != roomCount
                || (long)privateBeds + sharedBeds + crowdedBeds + highDensityBeds != enclosedBeds
                || privacyPercent.isPresent() != (scanComplete && enclosedBeds > 0)
                || (privacyPercent.isPresent() && (privacyPercent.getAsInt() < 0 || privacyPercent.getAsInt() > 100))
                || (scanComplete && unknownBeds != 0)) throw new IllegalArgumentException("Invalid Housing counts");
    }
    public static HousingSnapshot unavailable(int population, int beds) {
        return new HousingSnapshot(population, beds, 0, 0, 0, 0, 0, 0, beds, false);
    }
    public OptionalInt capacityPercent() {
        return !scanComplete || population == 0 ? OptionalInt.empty()
                : OptionalInt.of((int)Math.min(100, ((long)enclosedBeds * 100 + population / 2) / population));
    }
    public int unhousedResidents() { return scanComplete ? Math.max(0, population - enclosedBeds) : 0; }
    public int spareHousingCapacity() { return scanComplete ? Math.max(0, enclosedBeds - population) : 0; }
    public HousingCapacityState state() {
        return !scanComplete ? HousingCapacityState.SCAN_INCOMPLETE : population == 0 ? HousingCapacityState.NO_RESIDENTS
                : population > enclosedBeds ? HousingCapacityState.SHORTAGE : HousingCapacityState.SUFFICIENT;
    }
}
