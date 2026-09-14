package dev.conner.hometown.civic;

/** Stable livestock identifiers used by persisted Animal Farm policy and network controls. */
public enum LivestockSpecies {
    COW("cow", "Cows"),
    PIG("pig", "Pigs");

    private final String id;
    private final String displayName;

    LivestockSpecies(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }

    public static LivestockSpecies fromId(String id) {
        for (LivestockSpecies species : values()) if (species.id.equals(id)) return species;
        throw new IllegalArgumentException("Unknown livestock species: " + id);
    }
}
