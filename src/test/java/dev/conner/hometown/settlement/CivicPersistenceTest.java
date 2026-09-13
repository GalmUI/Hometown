package dev.conner.hometown.settlement;

import dev.conner.hometown.civic.*;
import dev.conner.hometown.history.HistoryEvent;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CivicPersistenceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private Settlement town() {
        return new Settlement(UUID.randomUUID(), "Oakridge", Level.OVERWORLD, new BlockPos(12, 68, -9), 64,
                UUID.randomUUID(), "Conner", 432000L);
    }

    @Test void v2MigrationPreservesR2DataAndAddsOnlyEmptyCivicState() {
        HometownSavedData original = new HometownSavedData();
        Settlement town = town();
        original.addSettlement(town);
        CompoundTag legacy = original.save(new CompoundTag(), null);
        legacy.putInt("DataVersion", 2);
        legacy.remove("Civic");

        HometownSavedData migrated = HometownSavedData.load(legacy, null);
        assertEquals(town, migrated.getSettlement(town.id()).orElseThrow());
        assertEquals(1, migrated.getHistory(town.id()).orElseThrow().events().size());
        assertEquals(HistoryEvent.Type.TOWN_FOUNDED, migrated.getHistory(town.id()).orElseThrow().events().getFirst().type());
        TownCivicState civic = migrated.getCivicState(town.id()).orElseThrow();
        assertFalse(civic.colorsConfigured());
        assertTrue(civic.unlocks().isEmpty());
        assertTrue(civic.facilities().isEmpty());
        assertTrue(migrated.isDirty());
    }

    @Test void v3RoundTripPreservesColorsProgressionAndFacilityMarker() {
        HometownSavedData original = new HometownSavedData();
        Settlement town = town();
        original.addSettlement(town);
        original.configureColors(town.id(), DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker marker = new FacilityMarker(town.id(), FacilityType.TOWN_HALL, town.dimension(),
                new BlockPos(20, 70, -2), FacilityMarkerSide.FRONT);
        original.updateCivicState(town.id(), state -> state
                .withUnlocked(List.of(ProgressionUnlock.TOWN_HALL, ProgressionUnlock.NOTICE_BOARD,
                        ProgressionUnlock.CIVIC_PROJECTS, ProgressionUnlock.STORAGE, ProgressionUnlock.ANIMAL_FARMS))
                .withFacility(marker));

        CompoundTag saved = original.save(new CompoundTag(), null);
        assertEquals(3, saved.getInt("DataVersion"));
        HometownSavedData loaded = HometownSavedData.load(saved, null);

        assertEquals(town, loaded.getSettlement(town.id()).orElseThrow());
        assertEquals(original.getCivicState(town.id()).orElseThrow(), loaded.getCivicState(town.id()).orElseThrow());
        assertFalse(loaded.isDirty());
    }

    @Test void colorMutationMarksDirtyOnlyWhenStateActuallyChanges() {
        HometownSavedData data = new HometownSavedData();
        Settlement town = town();
        data.addSettlement(town);
        data.setDirty(false);

        assertTrue(data.configureColors(town.id(), DyeColor.RED, DyeColor.GOLD));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.configureColors(town.id(), DyeColor.RED, DyeColor.GOLD));
        assertFalse(data.isDirty());
        assertThrows(IllegalStateException.class, () -> data.configureColors(town.id(), DyeColor.BLACK, DyeColor.WHITE));
        assertFalse(data.isDirty());
    }

    @Test void v3RejectsMissingForeignAndDuplicateCivicOwners() {
        HometownSavedData data = new HometownSavedData();
        Settlement town = town();
        data.addSettlement(town);

        CompoundTag missing = data.save(new CompoundTag(), null);
        missing.remove("Civic");
        assertThrows(IllegalStateException.class, () -> HometownSavedData.load(missing, null));

        CompoundTag foreign = data.save(new CompoundTag(), null);
        var foreignList = foreign.getList("Civic", Tag.TAG_COMPOUND);
        foreignList.getCompound(0).putUUID("SettlementId", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> HometownSavedData.load(foreign, null));

        CompoundTag duplicate = data.save(new CompoundTag(), null);
        var duplicateList = duplicate.getList("Civic", Tag.TAG_COMPOUND);
        duplicateList.add(duplicateList.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> HometownSavedData.load(duplicate, null));
    }

    @Test void savedDataRejectsForeignFacilityMutationBeforeDirtying() {
        HometownSavedData data = new HometownSavedData();
        Settlement town = town();
        data.addSettlement(town);
        data.setDirty(false);
        FacilityMarker foreign = new FacilityMarker(UUID.randomUUID(), FacilityType.TOWN_HALL, Level.OVERWORLD,
                BlockPos.ZERO, FacilityMarkerSide.FRONT);

        assertThrows(IllegalArgumentException.class,
                () -> data.updateCivicState(town.id(), state -> state.withFacility(foreign)));
        assertFalse(data.isDirty());
        assertTrue(data.civicState(town.id()).facilities().isEmpty());
    }
}
