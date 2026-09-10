package dev.conner.hometown.client;

/** Secondary navigation is independent of the four primary Ledger tabs. */
public enum DevelopmentSection {
    HOUSING, FOOD, SAFETY, COMFORT, COMMERCE, PROSPERITY;
    public String translationKey() { return "hometown.ledger.future." + name().toLowerCase(java.util.Locale.ROOT); }
}
