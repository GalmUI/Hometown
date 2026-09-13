package dev.conner.hometown.civic;

import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import io.netty.buffer.Unpooled;
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

    @Test void storageDetailUsesRegisteredFacilityAndLiveQualification() {
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
        assertTrue(snapshot.lines().stream().anyMatch(line -> line.label().equals("Recognized storage")
                && line.value().equals(StorageRules.MIN_STORAGE_BLOCKS + " / " + StorageRules.MIN_STORAGE_BLOCKS)));
        assertTrue(snapshot.lines().stream().anyMatch(line -> line.label().equals("Daily Meal")
                && line.value().equals("Not yet active")));
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
}
