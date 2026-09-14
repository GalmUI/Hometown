package dev.conner.hometown.civic;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Read-only live Animal Farm livestock observation paired with persisted policy/result state. */
public record AnimalFarmOperationsSnapshot(
        boolean livestockAvailable,
        Map<LivestockSpecies, Counts> counts,
        Map<LivestockSpecies, LivestockPolicy> policies,
        AnimalFarmCycleResult lastCycle) {

    public record Counts(int adults, int young, int namedAdults) {
        public Counts {
            if (adults < 0 || young < 0 || namedAdults < 0 || namedAdults > adults) {
                throw new IllegalArgumentException("Invalid livestock count");
            }
        }
        public Counts(int adults, int young) { this(adults, young, 0); }
        public int unnamedAdults() { return adults - namedAdults; }
    }

    public AnimalFarmOperationsSnapshot {
        EnumMap<LivestockSpecies, Counts> countCopy = new EnumMap<>(LivestockSpecies.class);
        countCopy.putAll(counts);
        EnumMap<LivestockSpecies, LivestockPolicy> policyCopy = new EnumMap<>(LivestockSpecies.class);
        policyCopy.putAll(policies);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            countCopy.putIfAbsent(species, new Counts(0, 0, 0));
            policyCopy.putIfAbsent(species, LivestockPolicy.defaults());
        }
        counts = Map.copyOf(countCopy);
        policies = Map.copyOf(policyCopy);
    }

    public Counts counts(LivestockSpecies species) { return counts.get(species); }
    public LivestockPolicy policy(LivestockSpecies species) { return policies.get(species); }
    public Optional<AnimalFarmCycleResult> lastCycleOptional() { return Optional.ofNullable(lastCycle); }
}
