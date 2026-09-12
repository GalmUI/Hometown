package dev.conner.hometown.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProsperityLedgerViewTest {
    @Test void defaultWeightsRenderAsPlayerFacingIndexPoints() {
        assertEquals("20%", ProsperityLedgerView.formatWeightShare(20, 100));
        assertEquals("20.0 pts", ProsperityLedgerView.formatIndexPoints(2000.0, 100));
        assertEquals("4.8 pts", ProsperityLedgerView.formatIndexPoints(483.3, 100));
    }

    @Test void customWeightsUseEffectiveShareOfEnabledWeight() {
        assertEquals("33.3%", ProsperityLedgerView.formatWeightShare(20, 60));
        assertEquals("33.3 pts", ProsperityLedgerView.formatIndexPoints(2000.0, 60));
        assertEquals("16.7%", ProsperityLedgerView.formatWeightShare(10, 60));
    }

    @Test void invalidEnabledWeightIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProsperityLedgerView.formatWeightShare(20, 0));
        assertThrows(IllegalArgumentException.class, () -> ProsperityLedgerView.formatIndexPoints(2000.0, 0));
    }
}
