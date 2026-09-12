package dev.conner.hometown.food;

import java.util.List;

/** One protected inventory pass: Reserves plus copied facts reused by Variety. */
public record FoodObservation(FoodSnapshot reserves, List<FoodStackFact> stackFacts) {
    public FoodObservation {
        java.util.Objects.requireNonNull(reserves);
        stackFacts = List.copyOf(stackFacts);
    }
}
