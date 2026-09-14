package dev.conner.hometown.civic;

import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.network.UpdateAnimalFarmPolicyPayload;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalFarmOperationsFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void policyProtectsBreedingPairsAndClampsCullThreshold() {
        LivestockPolicy defaults = LivestockPolicy.defaults();
        assertEquals(1, defaults.breedingPairs());
        assertEquals(2, defaults.protectedAdults());
        assertEquals(4, defaults.cullAbove());

        LivestockPolicy expanded = defaults.withBreedingPairs(3);
        assertEquals(3, expanded.breedingPairs());
        assertEquals(6, expanded.protectedAdults());
        assertEquals(6, expanded.cullAbove());
        assertEquals(6, expanded.withCullAbove(2).cullAbove());
        assertEquals(64, expanded.withCullAbove(1000).cullAbove());
    }

    @Test void cullingIsSurplusOnlyBoundedAndProtectsNamedAdults() {
        LivestockPolicy policy = LivestockPolicy.defaults();
        assertEquals(0, AnimalFarmOperationsService.cullCount(2, 0, policy));
        assertEquals(0, AnimalFarmOperationsService.cullCount(4, 0, policy));
        assertEquals(1, AnimalFarmOperationsService.cullCount(5, 0, policy));
        assertEquals(AnimalFarmOperationsService.MAX_CULL_PER_SPECIES,
                AnimalFarmOperationsService.cullCount(20, 0, policy));
        assertEquals(0, AnimalFarmOperationsService.cullCount(5, 5, policy));
    }

    @Test void operationStateAndCycleRoundTrip() {
        UUID town = UUID.randomUUID();
        AnimalFarmOperationsSavedData data = new AnimalFarmOperationsSavedData();
        data.update(town, state -> state.withPolicy(LivestockSpecies.COW, new LivestockPolicy(2, 7)));
        AnimalFarmCycleResult result = new AnimalFarmCycleResult(7, 170000L,
                AnimalFarmCycleResult.Outcome.PROCESSED,
                8, 1, 5, 2, 1, 1, 2, 1, 2);
        assertTrue(data.recordCycle(town, result));

        CompoundTag tag = data.save(new CompoundTag(), null);
        AnimalFarmOperationsSavedData loaded = AnimalFarmOperationsSavedData.load(tag, null);
        assertEquals(2, loaded.state(town).policy(LivestockSpecies.COW).breedingPairs());
        assertEquals(7, loaded.state(town).policy(LivestockSpecies.COW).cullAbove());
        assertEquals(result, loaded.state(town).lastCycle());
        assertFalse(loaded.state(town).lastCycle().retryable());
    }

    @Test void retryableFailureCanBeReplacedButTerminalCycleCannotDoubleProcess() {
        UUID town = UUID.randomUUID();
        AnimalFarmOperationsSavedData data = new AnimalFarmOperationsSavedData();
        AnimalFarmCycleResult full = new AnimalFarmCycleResult(4, 100000L,
                AnimalFarmCycleResult.Outcome.OUTPUT_STORAGE_FULL,
                5, 0, 4, 0, 0, 0, 0, 0, 0);
        AnimalFarmCycleResult processed = new AnimalFarmCycleResult(4, 100200L,
                AnimalFarmCycleResult.Outcome.PROCESSED,
                5, 0, 4, 0, 1, 0, 2, 1, 0);
        assertTrue(data.recordCycle(town, full));
        assertTrue(data.recordCycle(town, processed));
        assertFalse(data.recordCycle(town, full));
        assertEquals(processed, data.state(town).lastCycle());
    }

    @Test void clockRewindCanReplaceOnlyNonMutatingFailure() {
        UUID town = UUID.randomUUID();
        AnimalFarmOperationsSavedData data = new AnimalFarmOperationsSavedData();
        AnimalFarmCycleResult futureFailure = new AnimalFarmCycleResult(20, 500000L,
                AnimalFarmCycleResult.Outcome.LIVESTOCK_UNAVAILABLE,
                0, 0, 0, 0, 0, 0, 0, 0, 0);
        AnimalFarmCycleResult earlierSuccess = new AnimalFarmCycleResult(2, 520000L,
                AnimalFarmCycleResult.Outcome.NO_SURPLUS,
                2, 0, 2, 0, 0, 0, 0, 0, 0);
        assertTrue(data.recordCycle(town, futureFailure));
        assertTrue(data.recordCycle(town, earlierSuccess));
        assertEquals(earlierSuccess, data.state(town).lastCycle());
    }

    @Test void interactivePayloadsRoundTrip() {
        UUID id = UUID.randomUUID();
        var controls = new FacilityDetailSnapshotPayload.AnimalFarmControls(1, 4, 2, 6);
        var snapshot = new FacilityDetailSnapshotPayload(id, "Osea", DyeColor.BLUE, DyeColor.WHITE,
                FacilityType.ANIMAL_FARM, true,
                java.util.List.of(FacilityDetailSnapshotPayload.Line.section("Livestock")), controls);
        FriendlyByteBuf snapshotBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityDetailSnapshotPayload.STREAM_CODEC.encode(snapshotBuffer, snapshot);
            assertEquals(snapshot, FacilityDetailSnapshotPayload.STREAM_CODEC.decode(snapshotBuffer));
        } finally {
            snapshotBuffer.release();
        }

        var request = new UpdateAnimalFarmPolicyPayload(id, LivestockSpecies.COW,
                UpdateAnimalFarmPolicyPayload.Setting.CULL_ABOVE, -1);
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            UpdateAnimalFarmPolicyPayload.STREAM_CODEC.encode(requestBuffer, request);
            assertEquals(request, UpdateAnimalFarmPolicyPayload.STREAM_CODEC.decode(requestBuffer));
        } finally {
            requestBuffer.release();
        }
    }

    @Test void facilityDetailShowsDefaultBreedingStockAndControls() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        FacilityMarker farm = new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(14, 64, 14), FacilityMarkerSide.FRONT);
        data.registerAnimalFarm(id, farm, 48000L);
        var qualified = new AnimalFarmQualifier.Result(true, AnimalFarmQualifier.Reason.QUALIFIED,
                null, 1, 1, 2, 24, true);

        EnumMap<LivestockSpecies, AnimalFarmOperationsSnapshot.Counts> counts = new EnumMap<>(LivestockSpecies.class);
        counts.put(LivestockSpecies.COW, new AnimalFarmOperationsSnapshot.Counts(2, 0, 0));
        counts.put(LivestockSpecies.PIG, new AnimalFarmOperationsSnapshot.Counts(2, 0, 0));
        AnimalFarmOperationsSnapshot operations = new AnimalFarmOperationsSnapshot(true, counts,
                Map.of(LivestockSpecies.COW, LivestockPolicy.defaults(),
                        LivestockSpecies.PIG, LivestockPolicy.defaults()), null);
        var detail = FacilityDetailService.animalFarmSnapshotFor(
                town, data.civicState(id), farm, qualified, operations, 0L);

        assertNotNull(detail.animalFarmControls());
        assertEquals(1, detail.animalFarmControls().cowBreedingPairs());
        assertEquals(4, detail.animalFarmControls().cowCullAbove());
        assertTrue(hasRow(detail, "Cows", "2 adults, 0 young"));
        assertTrue(hasRow(detail, "Cow cullable surplus", "0"));
        assertTrue(hasRow(detail, "Pigs", "2 adults, 0 young"));
        assertTrue(hasRow(detail, "Next cycle", "Today — late workday"));
    }

    @Test void workCycleLabelMovesFromTodayToDueToNextDay() {
        assertEquals("Today — late workday", AnimalFarmOperationsService.nextCycleLabel(9000L, Optional.empty()));
        assertEquals("Due now", AnimalFarmOperationsService.nextCycleLabel(11000L, Optional.empty()));
        AnimalFarmCycleResult done = new AnimalFarmCycleResult(0, 11000L,
                AnimalFarmCycleResult.Outcome.NO_SURPLUS,
                2, 0, 2, 0, 0, 0, 0, 0, 0);
        assertEquals("Next workday", AnimalFarmOperationsService.nextCycleLabel(11000L, Optional.of(done)));
    }

    private static boolean hasRow(FacilityDetailSnapshotPayload snapshot, String label, String value) {
        return snapshot.lines().stream().anyMatch(line -> line.label().equals(label) && line.value().equals(value));
    }
}
