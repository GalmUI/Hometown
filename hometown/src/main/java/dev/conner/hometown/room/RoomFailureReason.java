package dev.conner.hometown.room;

public enum RoomFailureReason {
    NONE("None"),
    ESCAPED_TO_OUTSIDE("Interior connects to outside"),
    MAX_VOLUME_EXCEEDED("Maximum room volume exceeded"),
    MAX_DISTANCE_EXCEEDED("Maximum search distance exceeded"),
    NO_VALID_INTERIOR_START("No intact bed or valid interior start"),
    CHUNK_UNAVAILABLE("Required chunk is not loaded"),
    SCAN_BUDGET_EXCEEDED("Maximum scan work budget exceeded");

    private final String description;
    RoomFailureReason(String description) { this.description = description; }
    public String description() { return description; }
}
