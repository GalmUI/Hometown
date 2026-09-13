package dev.conner.hometown.civic;

import dev.conner.hometown.history.HistoryEvent;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void storageSignGrammarIsNarrowButCaseAndWhitespaceFriendly() {
        assertTrue(StorageSignGrammar.matches("[Hometown]", "Storage"));
        assertTrue(StorageSignGrammar.matches("  [HOMETOWN] ", "  storage  "));
        assertFalse(StorageSignGrammar.matches("Hometown", "Storage"));
        assertFalse(StorageSignGrammar.matches("[Hometown]", "Storehouse"));
        assertFalse(StorageSignGrammar.matches("[Hometown]", "Town Hall"));
    }

    @Test void firstStoragePersistsAndHistoryOccursOnlyOnceAcrossReplacement() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);

        FacilityMarker first = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);
        data.setDirty(false);
        var firstCommit = data.registerStorage(id, first, 48000L);
        assertTrue(firstCommit.changed());
        assertTrue(firstCommit.firstEstablishment());
        assertTrue(data.isDirty());
        assertEquals(first, data.civicState(id).facility(FacilityType.STORAGE).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.STORAGE_ESTABLISHED).count());

        data.setDirty(false);
        var duplicate = data.registerStorage(id, first, 72000L);
        assertFalse(duplicate.changed());
        assertFalse(duplicate.firstEstablishment());
        assertFalse(data.isDirty());

        FacilityMarker replacement = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(20, 64, 20), FacilityMarkerSide.BACK);
        var replacementCommit = data.registerStorage(id, replacement, 96000L);
        assertTrue(replacementCommit.changed());
        assertFalse(replacementCommit.firstEstablishment());
        assertEquals(replacement, data.civicState(id).facility(FacilityType.STORAGE).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.STORAGE_ESTABLISHED).count());

        CompoundTag saved = data.save(new CompoundTag(), null);
        HometownSavedData loaded = HometownSavedData.load(saved, null);
        assertEquals(replacement, loaded.civicState(id).facility(FacilityType.STORAGE).orElseThrow());
        assertEquals(1, loaded.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.STORAGE_ESTABLISHED).count());
    }

    @Test void storageCannotBeCommittedBeforeTownHallUnlocksIt() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);

        assertThrows(IllegalStateException.class, () -> data.registerStorage(id, storage, 24000L));
        assertTrue(data.civicState(id).facility(FacilityType.STORAGE).isEmpty());
    }

    @Test void storageAndTownHallRemainIndependentFacilityMarkers() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker hall = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);
        data.registerTownHall(id, hall, 24000L);
        data.registerStorage(id, storage, 48000L);

        assertEquals(hall, data.civicState(id).facility(FacilityType.TOWN_HALL).orElseThrow());
        assertEquals(storage, data.civicState(id).facility(FacilityType.STORAGE).orElseThrow());
    }
}
