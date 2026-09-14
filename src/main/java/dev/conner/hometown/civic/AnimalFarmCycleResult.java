package dev.conner.hometown.civic;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Durable summary of the most recent scheduled Animal Farm livestock cycle. */
public record AnimalFarmCycleResult(
        long day,
        long processedGameTime,
        Outcome outcome,
        int cowAdults,
        int cowYoung,
        int pigAdults,
        int pigYoung,
        int cowsCulled,
        int pigsCulled,
        int beefProduced,
        int leatherProduced,
        int porkchopsProduced) {

    public enum Outcome {
        PROCESSED,
        NO_SURPLUS,
        FACILITY_UNAVAILABLE,
        LIVESTOCK_UNAVAILABLE,
        OUTPUT_STORAGE_FULL
    }

    public AnimalFarmCycleResult {
        Objects.requireNonNull(outcome);
        if (cowAdults < 0 || cowYoung < 0 || pigAdults < 0 || pigYoung < 0
                || cowsCulled < 0 || pigsCulled < 0 || beefProduced < 0
                || leatherProduced < 0 || porkchopsProduced < 0) {
            throw new IllegalArgumentException("Negative Animal Farm cycle metric");
        }
        if (outcome != Outcome.PROCESSED
                && (cowsCulled != 0 || pigsCulled != 0 || beefProduced != 0
                || leatherProduced != 0 || porkchopsProduced != 0)) {
            throw new IllegalArgumentException("Non-processing Animal Farm outcome cannot report production");
        }
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Day", day);
        tag.putLong("ProcessedGameTime", processedGameTime);
        tag.putString("Outcome", outcome.name());
        tag.putInt("CowAdults", cowAdults);
        tag.putInt("CowYoung", cowYoung);
        tag.putInt("PigAdults", pigAdults);
        tag.putInt("PigYoung", pigYoung);
        tag.putInt("CowsCulled", cowsCulled);
        tag.putInt("PigsCulled", pigsCulled);
        tag.putInt("BeefProduced", beefProduced);
        tag.putInt("LeatherProduced", leatherProduced);
        tag.putInt("PorkchopsProduced", porkchopsProduced);
        return tag;
    }

    static AnimalFarmCycleResult fromTag(CompoundTag tag) {
        try {
            return new AnimalFarmCycleResult(
                    tag.getLong("Day"), tag.getLong("ProcessedGameTime"),
                    Outcome.valueOf(tag.getString("Outcome")),
                    tag.getInt("CowAdults"), tag.getInt("CowYoung"),
                    tag.getInt("PigAdults"), tag.getInt("PigYoung"),
                    tag.getInt("CowsCulled"), tag.getInt("PigsCulled"),
                    tag.getInt("BeefProduced"), tag.getInt("LeatherProduced"),
                    tag.getInt("PorkchopsProduced"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid Animal Farm cycle state", ex);
        }
    }
}
