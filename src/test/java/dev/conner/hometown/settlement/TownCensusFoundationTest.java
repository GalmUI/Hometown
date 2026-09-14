package dev.conner.hometown.settlement;

import dev.conner.hometown.network.data.ResidentSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownCensusFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static SettlementStats completeStats(int population) {
        java.util.ArrayList<ResidentSummary> residents = new java.util.ArrayList<>();
        for (int i = 0; i < population; i++) {
            residents.add(new ResidentSummary("Resident " + i, "hometown.ledger.unemployed", false));
        }
        return new SettlementStats(population, population + 2, Math.max(0, population - 1),
                population == 0 ? 0 : 1, SettlementStats.Availability.COMPLETE, residents);
    }

    @Test void completeCensusRoundTripsAsTrustedStats() {
        TownCensusState state = TownCensusState.from(completeStats(7), 12000L);
        assertEquals(state, TownCensusState.fromTag(state.toTag()));
        assertEquals(7, state.stats().population());
        assertEquals(SettlementStats.Availability.COMPLETE, state.stats().availability());
    }

    @Test void partialObservationCannotBecomeTrustedCensus() {
        SettlementStats partial = new SettlementStats(3, 4, 1, 1,
                SettlementStats.Availability.PARTIAL,
                List.of(new ResidentSummary("A", "hometown.ledger.unemployed", false),
                        new ResidentSummary("B", "hometown.ledger.unemployed", false),
                        new ResidentSummary("C", "hometown.ledger.unemployed", false)));
        assertThrows(IllegalArgumentException.class, () -> TownCensusState.from(partial, 100L));
    }

    @Test void savedDataKeepsNewestCompleteCensusAndPersistsIt() {
        UUID id = UUID.randomUUID();
        TownCensusSavedData data = new TownCensusSavedData();
        TownCensusState first = TownCensusState.from(completeStats(5), 6000L);
        TownCensusState newer = TownCensusState.from(completeStats(8), 12000L);
        TownCensusState older = TownCensusState.from(completeStats(2), 3000L);

        assertTrue(data.record(id, first));
        assertFalse(data.record(id, older));
        assertTrue(data.record(id, newer));
        assertEquals(8, data.get(id).orElseThrow().population());

        CompoundTag tag = data.save(new CompoundTag(), null);
        TownCensusSavedData loaded = TownCensusSavedData.load(tag, null);
        assertEquals(newer, loaded.get(id).orElseThrow());
    }

    @Test void freshnessSeparatesUiStalenessFromOperationalExpiry() {
        TownCensusState state = TownCensusState.from(completeStats(6), 1000L);
        assertEquals(TownCensusService.Freshness.FRESH,
                TownCensusService.freshness(state, 1000L + TownCensusService.FRESH_TICKS));
        assertEquals(TownCensusService.Freshness.STALE,
                TownCensusService.freshness(state, 1001L + TownCensusService.FRESH_TICKS));
        assertEquals(TownCensusService.Freshness.STALE,
                TownCensusService.freshness(state, 1000L + TownCensusService.MAX_OPERATION_AGE_TICKS));
        assertEquals(TownCensusService.Freshness.EXPIRED,
                TownCensusService.freshness(state, 1001L + TownCensusService.MAX_OPERATION_AGE_TICKS));
        assertTrue(TownCensusService.warning(Optional.of(state), 1001L + TownCensusService.FRESH_TICKS));
    }
}
