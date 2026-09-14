package dev.conner.hometown.civic;

import dev.conner.hometown.food.DailyMealState;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FacilityDetailFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void facilityDetailPayloadRoundTripsBoundedRows() {
        UUID id = UUID.randomUUID();
        var payload = new FacilityDetailSnapshotPayload(id, "Osea", DyeColor.BLUE, DyeColor.WHITE,
                FacilityType.STORAGE, true, java.util.List.of(
                FacilityDetailSnapshotPayload.Line.section("Status"),
                FacilityDetailSnapshotPayload.Line.row("State", "Established — Active", FacilityDetailSnapshotPayload.Tone.GOOD),
                FacilityDetailSnapshotPayload.Line.note("Town food reserve", FacilityDetailSnapshotPayload.Tone.NORMAL)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityDetailSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, FacilityDetailSnapshotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void storageDetailWithoutTrustedCensusShowsWaitState() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(10, 64, 10), FacilityMarkerSide.FRONT);
        data.registerStorage(id, storage, 48000L);

        var qualified = new StorageQualifier.Result(true, StorageQualifier.Reason.QUALIFIED,
                null, 8, StorageRules.MIN_STORAGE_BLOCKS, 0);
        var snapshot = FacilityDetailService.storageSnapshotFor(town, data.civicState(id), storage, qualified);
        assertEquals(FacilityType.STORAGE, snapshot.facilityType());
        assertTrue(snapshot.active());
        assertTrue(hasRow(snapshot, "Recognized storage",
                StorageRules.MIN_STORAGE_BLOCKS + " / " + StorageRules.MIN_STORAGE_BLOCKS));
        assertTrue(hasRow(snapshot, "Daily Meal", "Waiting for census"));
        assertTrue(hasRow(snapshot, "Next meal", "Today at sunset"));
    }

    @Test void storageDetailCarriesPersistedDailyMealResultAndReserve() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(10, 64, 10), FacilityMarkerSide.FRONT);
        data.registerStorage(id, storage, 48000L);
        var qualified = new StorageQualifier.Result(true, StorageQualifier.Reason.QUALIFIED,
                null, 8, StorageRules.MIN_STORAGE_BLOCKS, 0);
        var meal = new DailyMealState(2, 60000L, DailyMealState.Source.STORAGE, DailyMealState.Outcome.FED,
                10, 20, 200, 204, 780, 1, 0);
        var snapshot = FacilityDetailService.storageSnapshotFor(town, data.civicState(id), storage, qualified,
                Optional.of(meal), 60000L);
        assertTrue(hasRow(snapshot, "Daily Meal", "Fed 204 / 200"));
        assertTrue(hasRow(snapshot, "Last target", "204 / 200"));
        assertTrue(hasRow(snapshot, "Last source", "Town Storage"));
        assertTrue(hasRow(snapshot, "Known reserve after meal", "780"));
        assertTrue(snapshot.lines().stream().anyMatch(line -> line.value().contains("unresolved loot container")));
    }

    @Test void unavailableStorageDetailPreservesEstablishmentAndExplainsFailure() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(10, 64, 10), FacilityMarkerSide.FRONT);
        data.registerStorage(id, storage, 48000L);

        var failed = new StorageQualifier.Result(false, StorageQualifier.Reason.NOT_ENOUGH_STORAGE,
                null, 5, 3, 0);
        var snapshot = FacilityDetailService.storageSnapshotFor(town, data.civicState(id), storage, failed);
        assertFalse(snapshot.active());
        assertTrue(snapshot.lines().stream().anyMatch(line -> line.value().contains("Storage needs at least")));
        assertTrue(data.civicState(id).facility(FacilityType.STORAGE).isPresent());
    }

    @Test void animalFarmDetailReportsBuildingPaddockAndPendingLivestock() {
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
                null, AnimalFarmRules.MIN_STORAGE_BLOCKS, AnimalFarmRules.MIN_LOOMS,
                AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS, 24, true);
        var snapshot = FacilityDetailService.animalFarmSnapshotFor(town, data.civicState(id), farm, qualified);
        assertEquals(FacilityType.ANIMAL_FARM, snapshot.facilityType());
        assertTrue(snapshot.active());
        assertTrue(hasRow(snapshot, "Farm building", "Qualified"));
        assertTrue(hasRow(snapshot, "Recognized storage", "1 / 1"));
        assertTrue(hasRow(snapshot, "Looms", "1 / 1"));
        assertTrue(hasRow(snapshot, "Building connections", "2 / 2"));
        assertTrue(hasRow(snapshot, "Connected fences/gates", "24 / 16"));
        assertTrue(hasRow(snapshot, "Enclosure", "Closed"));
        assertTrue(hasRow(snapshot, "Production", "Not yet active"));
        assertTrue(hasRow(snapshot, "Animals", "Not yet tracked"));
    }

    @Test void unavailableAnimalFarmDetailExplainsOpenPaddockWithoutLosingEstablishment() {
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

        var failed = new AnimalFarmQualifier.Result(false, AnimalFarmQualifier.Reason.PADDOCK_OPEN,
                null, AnimalFarmRules.MIN_STORAGE_BLOCKS, AnimalFarmRules.MIN_LOOMS,
                AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS, 24, false);
        var snapshot = FacilityDetailService.animalFarmSnapshotFor(town, data.civicState(id), farm, failed);
        assertFalse(snapshot.active());
        assertTrue(hasRow(snapshot, "Enclosure", "Open"));
        assertTrue(snapshot.lines().stream().anyMatch(line -> line.value().contains("does not form an enclosed paddock")));
        assertTrue(data.civicState(id).facility(FacilityType.ANIMAL_FARM).isPresent());
    }

    private static boolean hasRow(FacilityDetailSnapshotPayload snapshot, String label, String value) {
        return snapshot.lines().stream().anyMatch(line -> line.label().equals(label) && line.value().equals(value));
    }
}
