package dev.conner.hometown.food;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Known server observations. Partial values must never be presented as complete town totals. */
public record FoodSnapshot(int population, int foodContainers, int foodStacks, int uniqueFoodTypes,
        long totalNutrition, long dailyNutritionRequirement, OptionalDouble reserveDays,
        Optional<FoodSecurityState> state, FoodScanStatus scanStatus, OptionalInt barPercent, FoodScanDiagnostics diagnostics) {
    public enum FoodSecurityState { NO_RESIDENTS, EMPTY, CRITICAL, LOW, STABLE, STOCKED }
    public FoodSnapshot {
        java.util.Objects.requireNonNull(reserveDays); java.util.Objects.requireNonNull(state); java.util.Objects.requireNonNull(barPercent);
        java.util.Objects.requireNonNull(scanStatus); java.util.Objects.requireNonNull(diagnostics);
        if (population < 0 || foodContainers < 0 || foodStacks < 0 || uniqueFoodTypes < 0 || totalNutrition < 0 || dailyNutritionRequirement < 0
                || foodContainers > foodStacks || uniqueFoodTypes > foodStacks
                || state.isPresent() != (scanStatus == FoodScanStatus.COMPLETE)
                || (scanStatus == FoodScanStatus.COMPLETE && (!diagnostics.reasons().isEmpty() || !diagnostics.populationComplete()))
                || (scanStatus != FoodScanStatus.COMPLETE && diagnostics.reasons().isEmpty())
                || reserveDays.isPresent() != (scanStatus != FoodScanStatus.UNAVAILABLE && population > 0 && diagnostics.populationComplete())
                || reserveDays.isPresent() != barPercent.isPresent()
                || (reserveDays.isPresent() && (!Double.isFinite(reserveDays.getAsDouble()) || reserveDays.getAsDouble() < 0))
                || (barPercent.isPresent() && (barPercent.getAsInt() < 0 || barPercent.getAsInt() > 100)))
            throw new IllegalArgumentException("Invalid Food snapshot");
    }
    public boolean scanComplete() { return scanStatus == FoodScanStatus.COMPLETE; }
    public static FoodSnapshot unavailable(int population) { return unavailable(population,FoodScanReason.NO_SETTLEMENT_DATA); }
    public static FoodSnapshot unavailable(int population, FoodScanReason reason) {
        return new FoodSnapshot(population,0,0,0,0,0,OptionalDouble.empty(),Optional.empty(),FoodScanStatus.UNAVAILABLE,
                OptionalInt.empty(),FoodScanDiagnostics.empty(Set.of(reason),false));
    }
}
