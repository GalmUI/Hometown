package dev.conner.hometown.settlement;

import dev.conner.hometown.commerce.CommerceResidentFact;
import java.util.List;

/** One existing resident pass, exposed as protected stats plus copied facts for Commerce. */
public record SettlementObservation(SettlementStats stats, List<CommerceResidentFact> residentFacts,
        int residentInspections, int duplicateResidents) {
    public SettlementObservation {
        java.util.Objects.requireNonNull(stats);
        residentFacts = List.copyOf(residentFacts);
        if (residentInspections < 0 || duplicateResidents < 0 || duplicateResidents > residentInspections)
            throw new IllegalArgumentException("Invalid resident observation counters");
    }
}
