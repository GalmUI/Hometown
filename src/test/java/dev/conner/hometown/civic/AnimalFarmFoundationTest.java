package dev.conner.hometown.civic;

import dev.conner.hometown.history.HistoryEvent;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.HashSet;
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

class AnimalFarmFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void animalFarmSignGrammarIsNarrowButCaseAndWhitespaceFriendly() {
        assertTrue(AnimalFarmSignGrammar.matches("[Hometown]", "Animal Farm"));
        assertTrue(AnimalFarmSignGrammar.matches("  [HOMETOWN] ", " animal   farm "));
        assertFalse(AnimalFarmSignGrammar.matches("Hometown", "Animal Farm"));
        assertFalse(AnimalFarmSignGrammar.matches("[Hometown]", "AnimalFarm"));
        assertFalse(AnimalFarmSignGrammar.matches("[Hometown]", "Farm"));
    }

    @Test void paddockRuleRequiresTwoAttachmentsSixteenBarriersAndClosure() {
        assertEquals(AnimalFarmQualifier.Reason.NOT_ENOUGH_PADDOCK_ATTACHMENTS,
                AnimalFarmQualifier.paddockRuleReason(1, 16, true));
        assertEquals(AnimalFarmQualifier.Reason.NOT_ENOUGH_PADDOCK_BARRIERS,
                AnimalFarmQualifier.paddockRuleReason(2, 15, true));
        assertEquals(AnimalFarmQualifier.Reason.PADDOCK_OPEN,
                AnimalFarmQualifier.paddockRuleReason(2, 16, false));
        assertEquals(AnimalFarmQualifier.Reason.QUALIFIED,
                AnimalFarmQualifier.paddockRuleReason(2, 16, true));
    }

    @Test void buildingWallMayCloseTheAttachedPaddock() {
        Set<BlockPos> building = new HashSet<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) building.add(new BlockPos(x, 64, z));
        }
        Set<BlockPos> fence = new HashSet<>();
        for (int z = 3; z <= 6; z++) {
            fence.add(new BlockPos(0, 64, z));
            fence.add(new BlockPos(2, 64, z));
        }
        for (int x = 0; x <= 2; x++) fence.add(new BlockPos(x, 64, 6));

        assertTrue(AnimalFarmQualifier.hasEnclosedPaddock(building, fence));
        fence.remove(new BlockPos(1, 64, 6));
        assertFalse(AnimalFarmQualifier.hasEnclosedPaddock(building, fence));
    }

    @Test void firstAnimalFarmPersistsAndHistoryOccursOnlyOnceAcrossReplacement() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);

        FacilityMarker first = new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);
        data.setDirty(false);
        var firstCommit = data.registerAnimalFarm(id, first, 48000L);
        assertTrue(firstCommit.changed());
        assertTrue(firstCommit.firstEstablishment());
        assertTrue(data.isDirty());
        assertEquals(first, data.civicState(id).facility(FacilityType.ANIMAL_FARM).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.ANIMAL_FARM_ESTABLISHED).count());

        data.setDirty(false);
        var duplicate = data.registerAnimalFarm(id, first, 72000L);
        assertFalse(duplicate.changed());
        assertFalse(duplicate.firstEstablishment());
        assertFalse(data.isDirty());

        FacilityMarker replacement = new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(20, 64, 20), FacilityMarkerSide.BACK);
        var replacementCommit = data.registerAnimalFarm(id, replacement, 96000L);
        assertTrue(replacementCommit.changed());
        assertFalse(replacementCommit.firstEstablishment());
        assertEquals(replacement, data.civicState(id).facility(FacilityType.ANIMAL_FARM).orElseThrow());
        assertEquals(1, data.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.ANIMAL_FARM_ESTABLISHED).count());

        CompoundTag saved = data.save(new CompoundTag(), null);
        HometownSavedData loaded = HometownSavedData.load(saved, null);
        assertEquals(replacement, loaded.civicState(id).facility(FacilityType.ANIMAL_FARM).orElseThrow());
        assertEquals(1, loaded.history(id).events().stream()
                .filter(event -> event.type() == HistoryEvent.Type.ANIMAL_FARM_ESTABLISHED).count());
    }

    @Test void animalFarmCannotBeCommittedBeforeTownHallUnlocksIt() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker farm = new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);

        assertThrows(IllegalStateException.class, () -> data.registerAnimalFarm(id, farm, 24000L));
        assertTrue(data.civicState(id).facility(FacilityType.ANIMAL_FARM).isEmpty());
    }

    @Test void hallStorageAndAnimalFarmRemainIndependentFacilityMarkers() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker hall = new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT);
        FacilityMarker storage = new FacilityMarker(id, FacilityType.STORAGE, Level.OVERWORLD,
                new BlockPos(10, 64, 10), FacilityMarkerSide.FRONT);
        FacilityMarker farm = new FacilityMarker(id, FacilityType.ANIMAL_FARM, Level.OVERWORLD,
                new BlockPos(20, 64, 20), FacilityMarkerSide.FRONT);
        data.registerTownHall(id, hall, 24000L);
        data.registerStorage(id, storage, 48000L);
        data.registerAnimalFarm(id, farm, 72000L);

        assertEquals(hall, data.civicState(id).facility(FacilityType.TOWN_HALL).orElseThrow());
        assertEquals(storage, data.civicState(id).facility(FacilityType.STORAGE).orElseThrow());
        assertEquals(farm, data.civicState(id).facility(FacilityType.ANIMAL_FARM).orElseThrow());
    }
}
