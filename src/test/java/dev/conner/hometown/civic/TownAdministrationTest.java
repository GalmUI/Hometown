package dev.conner.hometown.civic;

import dev.conner.hometown.network.TownAdministrationSnapshotPayload;
import dev.conner.hometown.room.RoomGeometry;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import io.netty.buffer.Unpooled;
import java.util.Set;
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

class TownAdministrationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void administrationSnapshotRoundTripsBoundedIdentityColorsAndProgression() {
        UUID id = UUID.randomUUID();
        var snapshot = new TownAdministrationSnapshotPayload(
                id, "Osea", DyeColor.BLUE, DyeColor.WHITE,
                true, true, true, true, true, true, true, true, true, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TownAdministrationSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, TownAdministrationSnapshotPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();
            assertThrows(IllegalArgumentException.class, () -> new TownAdministrationSnapshotPayload(
                    id, "Osea", DyeColor.BLUE, DyeColor.BLUE,
                    true, true, true, true, true, true, true, true, true, true));
            assertThrows(IllegalArgumentException.class, () -> new TownAdministrationSnapshotPayload(
                    id, "Osea", DyeColor.BLUE, DyeColor.WHITE,
                    true, true, true, true, true, false, true, true, false, false));
            assertThrows(IllegalArgumentException.class, () -> new TownAdministrationSnapshotPayload(
                    id, "Osea", DyeColor.BLUE, DyeColor.WHITE,
                    true, true, true, true, true, false, false, true, false, true));
        } finally {
            buffer.release();
        }
    }

    @Test void establishedHallSnapshotCarriesUnlocksWithoutInventingFacilities() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker marker = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT);
        data.registerTownHall(id, marker, 24000L);

        var snapshot = TownAdministrationService.snapshotFor(town, data.civicState(id));
        assertEquals(id, snapshot.settlementId());
        assertEquals("Osea", snapshot.townName());
        assertEquals(DyeColor.BLUE, snapshot.primaryColor());
        assertEquals(DyeColor.WHITE, snapshot.secondaryColor());
        assertTrue(snapshot.townHallEstablished());
        assertTrue(snapshot.townHallActive());
        assertTrue(snapshot.noticeBoardUnlocked());
        assertTrue(snapshot.civicProjectsUnlocked());
        assertTrue(snapshot.storageUnlocked());
        assertFalse(snapshot.storageEstablished());
        assertFalse(snapshot.storageActive());
        assertTrue(snapshot.animalFarmsUnlocked());
        assertFalse(snapshot.animalFarmEstablished());
        assertFalse(snapshot.animalFarmActive());
    }

    @Test void establishedStorageCanBeReportedActiveWithoutChangingAnimalFarmState() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        data.registerStorage(id, new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(10, 64, 10), FacilityMarkerSide.FRONT), 48000L);

        var snapshot = TownAdministrationService.snapshotFor(town, data.civicState(id), true);
        assertTrue(snapshot.storageUnlocked());
        assertTrue(snapshot.storageEstablished());
        assertTrue(snapshot.storageActive());
        assertTrue(snapshot.animalFarmsUnlocked());
        assertFalse(snapshot.animalFarmEstablished());
        assertFalse(snapshot.animalFarmActive());
    }

    @Test void establishedAnimalFarmCanBeReportedActive() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);
        data.registerAnimalFarm(id, new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(20, 64, 20), FacilityMarkerSide.FRONT), 48000L);

        var snapshot = TownAdministrationService.snapshotFor(town, data.civicState(id), false, true);
        assertTrue(snapshot.animalFarmsUnlocked());
        assertTrue(snapshot.animalFarmEstablished());
        assertTrue(snapshot.animalFarmActive());
    }

    @Test void administrationLecternMustBelongToTheValidatedHallRoom() {
        BlockPos interiorLectern = new BlockPos(1, 64, 1);
        BlockPos boundaryLectern = new BlockPos(2, 64, 1);
        RoomGeometry room = new RoomGeometry(BlockPos.ZERO, Set.of(),
                Set.of(interiorLectern), Set.of(boundaryLectern), true);
        TownHallQualifier.Result qualified = new TownHallQualifier.Result(
                true, TownHallQualifier.Reason.QUALIFIED, room, 20, 4, 1, 1, 0);

        assertTrue(TownAdministrationService.isLecternInValidatedHall(qualified, interiorLectern));
        assertTrue(TownAdministrationService.isLecternInValidatedHall(qualified, boundaryLectern));
        assertFalse(TownAdministrationService.isLecternInValidatedHall(qualified, new BlockPos(20, 64, 20)));
        assertFalse(TownAdministrationService.isLecternInValidatedHall(
                TownHallQualifier.Result.failed(TownHallQualifier.Reason.ROOM_INCOMPLETE), interiorLectern));
    }
}
