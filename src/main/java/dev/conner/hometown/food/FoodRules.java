package dev.conner.hometown.food;

import dev.conner.hometown.config.HometownServerConfig;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import static dev.conner.hometown.food.FoodSnapshot.FoodSecurityState.*;

/** All arithmetic runs server-side. Thresholds are isolated for future configuration. */
public record FoodRules(int nutritionPerResidentPerDay, double lowDays, double stableDays, double stockedDays) {
    public static final FoodRules DEFAULT = new FoodRules(20,1,3,7);
    public FoodRules {
        if (nutritionPerResidentPerDay <= 0 || !Double.isFinite(stockedDays) || !(0 < lowDays && lowDays < stableDays && stableDays < stockedDays))
            throw new IllegalArgumentException("Invalid Food rules");
    }
    public static FoodRules current() { return new FoodRules(HometownServerConfig.NUTRITION_PER_RESIDENT_PER_DAY.get(),1,3,7); }
    public FoodSnapshot snapshot(int population, int containers, int stacks, int types, long nutrition) {
        return snapshot(population,containers,stacks,types,nutrition,FoodScanStatus.COMPLETE,FoodScanDiagnostics.empty(java.util.Set.of(),true));
    }
    public FoodSnapshot snapshot(int population, int containers, int stacks, int types, long nutrition,
                                 FoodScanStatus status, FoodScanDiagnostics diagnostics) {
        long daily = Math.multiplyExact((long)population, nutritionPerResidentPerDay);
        var days = population > 0 && diagnostics.populationComplete() && status != FoodScanStatus.UNAVAILABLE
                ? OptionalDouble.of((double)nutrition / daily) : OptionalDouble.empty();
        var state = java.util.Optional.<FoodSnapshot.FoodSecurityState>empty();
        if (status == FoodScanStatus.COMPLETE) state = java.util.Optional.of(population == 0 ? NO_RESIDENTS
                : nutrition == 0 ? EMPTY : days.getAsDouble() < lowDays ? CRITICAL : days.getAsDouble() < stableDays ? LOW
                : days.getAsDouble() < stockedDays ? STABLE : STOCKED);
        var bar = days.isPresent() ? OptionalInt.of((int)Math.round(Math.min(100,days.getAsDouble() / stockedDays * 100))) : OptionalInt.empty();
        return new FoodSnapshot(population,containers,stacks,types,nutrition,daily,days,state,status,bar,diagnostics);
    }
}
