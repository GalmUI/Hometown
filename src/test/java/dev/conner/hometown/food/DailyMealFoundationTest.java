package dev.conner.hometown.food;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyMealFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void nutritionRollIsDeterministicAndBoundedPerTownDay() {
        UUID town = UUID.fromString("f61f394e-cff2-4e65-be91-8ea07647e610");
        for (long day = 0; day < 200; day++) {
            int first = DailyMealService.nutritionPerResident(town, day);
            int second = DailyMealService.nutritionPerResident(town, day);
            assertEquals(first, second);
            assertTrue(first >= 16 && first <= 24);
        }
    }

    @Test void successAndShortageClassificationAreExplicit() {
        assertEquals(DailyMealState.Outcome.NO_RESIDENTS, DailyMealService.outcome(0, 0));
        assertEquals(DailyMealState.Outcome.SHORTAGE, DailyMealService.outcome(200, 199));
        assertEquals(DailyMealState.Outcome.FED, DailyMealService.outcome(200, 200));
        assertEquals(DailyMealState.Outcome.FED, DailyMealService.outcome(200, 204));
    }

    @Test void mealStateRoundTripsWithoutRerollOrLosingDiagnostics() {
        DailyMealState state = new DailyMealState(7, 180000L, DailyMealState.Source.STORAGE,
                DailyMealState.Outcome.SHORTAGE, 10, 21, 210, 188, 420, 1, 2);
        assertEquals(state, DailyMealState.fromTag(state.toTag()));
        assertTrue(state.warning());
        assertEquals("Shortage 188 / 210", DailyMealService.summary(java.util.Optional.of(state)));
    }

    @Test void savedDataAllowsAtMostOneRealMealPerDayAndPersistsLatestResult() {
        UUID id = UUID.randomUUID();
        DailyMealSavedData data = new DailyMealSavedData();
        DailyMealState day3 = new DailyMealState(3, 84000L, DailyMealState.Source.TOWN_HALL,
                DailyMealState.Outcome.FED, 4, 20, 80, 80, 120, 0, 0);
        DailyMealState sameDay = new DailyMealState(3, 85000L, DailyMealState.Source.TOWN_HALL,
                DailyMealState.Outcome.FED, 4, 18, 72, 72, 100, 0, 0);
        DailyMealState day4 = new DailyMealState(4, 108000L, DailyMealState.Source.STORAGE,
                DailyMealState.Outcome.SHORTAGE, 5, 22, 110, 90, 0, 0, 0);

        assertTrue(data.record(id, day3));
        assertFalse(data.record(id, sameDay));
        assertTrue(data.record(id, day4));
        assertEquals(day4, data.get(id).orElseThrow());

        CompoundTag tag = data.save(new CompoundTag(), null);
        DailyMealSavedData loaded = DailyMealSavedData.load(tag, null);
        assertEquals(day4, loaded.get(id).orElseThrow());
    }

    @Test void legacyPopulationUnavailableStateCanBeSafelyReplacedSameDay() {
        UUID id = UUID.randomUUID();
        DailyMealSavedData data = new DailyMealSavedData();
        DailyMealState waiting = new DailyMealState(5, 132000L, DailyMealState.Source.NONE,
                DailyMealState.Outcome.POPULATION_UNAVAILABLE, 0, 20, 0, 0, -1, 0, 0);
        DailyMealState fed = new DailyMealState(5, 133000L, DailyMealState.Source.STORAGE,
                DailyMealState.Outcome.FED, 4, 20, 80, 80, 120, 0, 0);
        assertTrue(data.record(id, waiting));
        assertTrue(data.record(id, fed));
        assertEquals(fed, data.get(id).orElseThrow());
        assertFalse(data.record(id, waiting));
    }

    @Test void sourceAndCensusWaitStatesStayDistinguishableForUi() {
        DailyMealState hall = new DailyMealState(1, 36000L, DailyMealState.Source.TOWN_HALL,
                DailyMealState.Outcome.FED, 3, 16, 48, 48, 64, 0, 0);
        DailyMealState storageUnavailable = new DailyMealState(2, 60000L, DailyMealState.Source.STORAGE,
                DailyMealState.Outcome.SOURCE_UNAVAILABLE, 3, 20, 60, 0, -1, 0, 1);
        DailyMealState waiting = new DailyMealState(2, 60000L, DailyMealState.Source.NONE,
                DailyMealState.Outcome.POPULATION_UNAVAILABLE, 0, 20, 0, 0, -1, 0, 0);
        assertFalse(hall.warning());
        assertTrue(storageUnavailable.warning());
        assertEquals("Storage unavailable", DailyMealService.summary(java.util.Optional.of(storageUnavailable)));
        assertEquals("Waiting for census", DailyMealService.summary(java.util.Optional.of(waiting)));
        assertEquals("Waiting for census", DailyMealService.currentSummary(java.util.Optional.empty(), 2 * 24000L + 13000L, false));
    }
}
