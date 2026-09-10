package dev.conner.hometown.settlement;

import dev.conner.hometown.network.data.ResidentSummary;
import java.util.List;

public record SettlementStats(int population, int beds, int employed, int professionDiversity,
                              Availability availability, List<ResidentSummary> residents) {
    public enum Availability { COMPLETE, PARTIAL, UNAVAILABLE }
    public SettlementStats { residents = List.copyOf(residents); }
    public static SettlementStats unavailable() {
        return new SettlementStats(0, 0, 0, 0, Availability.UNAVAILABLE, List.of());
    }
}
