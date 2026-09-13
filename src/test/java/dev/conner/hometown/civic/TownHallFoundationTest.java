package dev.conner.hometown.civic;

import dev.conner.hometown.history.HistoryEvent;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.Set;
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

class TownHallFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void signGrammarIsNarrowButWhitespaceAndCaseFriendly() {
        assertTrue(TownHallSignGrammar.matches("[Hometown]", "Town Hall"));
        assertTrue(TownHallSignGrammar.matches("  [HOMETOWN]  ", "  town   hall "));
        assertFalse(TownHallSignGrammar.matches("Hometown", "Town Hall"));
        assertFalse(TownHallSignGrammar.matches("[Hometown]", "Townhall"));
        assertFalse(TownHallSignGrammar.matches("[Hometown]", "Storage"));
    }

    @Test void firstHallAtomicallyUnlocksProgressionAndHistoryOnlyOnce() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 24000L);
        data.addSettlement(town, DyeColor.RED, DyeColor.YELLOW);
        data.setDirty(false);

        FacilityMarker first = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT);
        var firstCommit = data.registerTownHall(id, first, 48000L);
        assertTrue(firstCommit.changed());
        assertTrue(firstCommit.firstEstablishment());
        assertTrue(data.isDirty());
        var civic = data.civicState(id);
        assertEquals(Set.of(ProgressionUnlock.TOWN_HALL, ProgressionUnlock.NOTICE_BOARD,
                ProgressionUnlock.CIVIC_PROJECTS, ProgressionUnlock.STORAGE, ProgressionUnlock.ANIMAL_FARMS),
                civic.unlocks());
        assertEquals(first, civic.facility(FacilityType.TOWN_HALL).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.TOWN_HALL_ESTABLISHED).count());

        data.setDirty(false);
        var duplicate = data.registerTownHall(id, first, 72000L);
        assertFalse(duplicate.changed());
        assertFalse(duplicate.firstEstablishment());
        assertFalse(data.isDirty());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.TOWN_HALL_ESTABLISHED).count());

        FacilityMarker replacement = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(8, 64, 8), FacilityMarkerSide.BACK);
        var replacementCommit = data.registerTownHall(id, replacement, 96000L);
        assertTrue(replacementCommit.changed());
        assertFalse(replacementCommit.firstEstablishment());
        assertEquals(replacement, data.civicState(id).facility(FacilityType.TOWN_HALL).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.TOWN_HALL_ESTABLISHED).count());

        var saved = data.save(new CompoundTag(), null);
        var loaded = HometownSavedData.load(saved, null);
        assertEquals(replacement, loaded.civicState(id).facility(FacilityType.TOWN_HALL).orElseThrow());
        assertEquals(civic.unlocks(), loaded.civicState(id).unlocks());
        assertEquals(1, loaded.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.TOWN_HALL_ESTABLISHED).count());
    }

    @Test void HallCannotBeCommittedBeforeTownColorsExist() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Legacy", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town);
        FacilityMarker marker = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(1, 64, 1), FacilityMarkerSide.FRONT);
        assertThrows(IllegalStateException.class, () -> data.registerTownHall(id, marker, 1L));
        assertFalse(data.civicState(id).isUnlocked(ProgressionUnlock.TOWN_HALL));
        assertTrue(data.civicState(id).facility(FacilityType.TOWN_HALL).isEmpty());
    }
}
