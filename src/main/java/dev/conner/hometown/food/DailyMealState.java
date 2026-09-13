package dev.conner.hometown.food;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Durable result of the most recent Daily Meal processed for one Hometown. */
public record DailyMealState(
        long day,
        long processedGameTime,
        Source source,
        Outcome outcome,
        int population,
        int nutritionPerResident,
        long requiredNutrition,
        long consumedNutrition,
        long remainingKnownNutrition,
        int unresolvedLootContainers,
        int unavailableContainers) {

    public enum Source { NONE, TOWN_HALL, STORAGE }
    public enum Outcome {
        FED,
        SHORTAGE,
        NO_RESIDENTS,
        POPULATION_UNAVAILABLE,
        NO_TOWN_HALL,
        SOURCE_UNAVAILABLE
    }

    public DailyMealState {
        Objects.requireNonNull(source);
        Objects.requireNonNull(outcome);
        if (population < 0) throw new IllegalArgumentException("Negative Daily Meal population");
        if (nutritionPerResident < 0) throw new IllegalArgumentException("Negative Daily Meal nutrition roll");
        if (requiredNutrition < 0 || consumedNutrition < 0) throw new IllegalArgumentException("Negative Daily Meal nutrition");
        if (remainingKnownNutrition < -1) throw new IllegalArgumentException("Invalid Daily Meal remaining nutrition");
        if (unresolvedLootContainers < 0 || unavailableContainers < 0) throw new IllegalArgumentException("Negative Daily Meal container diagnostics");
        if (outcome == Outcome.FED && consumedNutrition < requiredNutrition) {
            throw new IllegalArgumentException("Fed Daily Meal must satisfy its target");
        }
        if (outcome == Outcome.SHORTAGE && consumedNutrition >= requiredNutrition) {
            throw new IllegalArgumentException("Shortage Daily Meal must miss its target");
        }
    }

    public boolean warning() {
        return outcome == Outcome.SHORTAGE || outcome == Outcome.POPULATION_UNAVAILABLE
                || outcome == Outcome.NO_TOWN_HALL || outcome == Outcome.SOURCE_UNAVAILABLE;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Day", day);
        tag.putLong("ProcessedGameTime", processedGameTime);
        tag.putString("Source", source.name());
        tag.putString("Outcome", outcome.name());
        tag.putInt("Population", population);
        tag.putInt("NutritionPerResident", nutritionPerResident);
        tag.putLong("RequiredNutrition", requiredNutrition);
        tag.putLong("ConsumedNutrition", consumedNutrition);
        tag.putLong("RemainingKnownNutrition", remainingKnownNutrition);
        tag.putInt("UnresolvedLootContainers", unresolvedLootContainers);
        tag.putInt("UnavailableContainers", unavailableContainers);
        return tag;
    }

    public static DailyMealState fromTag(CompoundTag tag) {
        try {
            return new DailyMealState(
                    tag.getLong("Day"),
                    tag.getLong("ProcessedGameTime"),
                    Source.valueOf(tag.getString("Source")),
                    Outcome.valueOf(tag.getString("Outcome")),
                    tag.getInt("Population"),
                    tag.getInt("NutritionPerResident"),
                    tag.getLong("RequiredNutrition"),
                    tag.getLong("ConsumedNutrition"),
                    tag.getLong("RemainingKnownNutrition"),
                    tag.getInt("UnresolvedLootContainers"),
                    tag.getInt("UnavailableContainers"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid Daily Meal state", ex);
        }
    }
}
