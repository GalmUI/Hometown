package dev.conner.hometown.civic;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownCivicStateTest {
    @Test void emptyStateHasNoInventedIdentityOrProgression() {
        TownCivicState state = TownCivicState.empty();
        assertFalse(state.colorsConfigured());
        assertTrue(state.primaryColor().isEmpty());
        assertTrue(state.secondaryColor().isEmpty());
        assertTrue(state.unlocks().isEmpty());
        assertTrue(state.facilities().isEmpty());
    }

    @Test void colorsAreDistinctOneTimeAndIdempotentForTheSameSelection() {
        TownCivicState state = TownCivicState.empty().withColors(DyeColor.RED, DyeColor.YELLOW);
        assertTrue(state.colorsConfigured());
        assertEquals(DyeColor.RED, state.primaryColor().orElseThrow());
        assertEquals(DyeColor.YELLOW, state.secondaryColor().orElseThrow());
        assertSame(state, state.withColors(DyeColor.RED, DyeColor.YELLOW));
        assertThrows(IllegalArgumentException.class, () -> TownCivicState.empty().withColors(DyeColor.BLUE, DyeColor.BLUE));
        assertThrows(IllegalStateException.class, () -> state.withColors(DyeColor.BLUE, DyeColor.WHITE));
    }

    @Test void roundTripPreservesColorsUnlocksAndGenericFacilityMarker() {
        UUID townId = UUID.randomUUID();
        FacilityMarker marker = new FacilityMarker(townId, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(12, 70, -4), FacilityMarkerSide.FRONT);
        TownCivicState original = TownCivicState.empty()
                .withColors(DyeColor.GREEN, DyeColor.WHITE)
                .withUnlocked(List.of(ProgressionUnlock.TOWN_HALL, ProgressionUnlock.STORAGE, ProgressionUnlock.ANIMAL_FARMS))
                .withFacility(marker);

        TownCivicState loaded = TownCivicState.fromTag(original.toTag(), townId);
        assertEquals(original, loaded);
        assertEquals(marker, loaded.facility(FacilityType.TOWN_HALL).orElseThrow());
    }

    @Test void malformedColorUnlockAndFacilityStateFailsClosed() {
        UUID townId = UUID.randomUUID();

        var inconsistentColors = TownCivicState.empty().toTag();
        inconsistentColors.putBoolean("ColorsConfigured", true);
        assertThrows(IllegalArgumentException.class, () -> TownCivicState.fromTag(inconsistentColors, townId));

        var duplicateUnlocks = TownCivicState.empty().toTag();
        var unlocks = duplicateUnlocks.getList("Unlocks", Tag.TAG_STRING);
        unlocks.add(StringTag.valueOf(ProgressionUnlock.TOWN_HALL.name()));
        unlocks.add(StringTag.valueOf(ProgressionUnlock.TOWN_HALL.name()));
        assertThrows(IllegalArgumentException.class, () -> TownCivicState.fromTag(duplicateUnlocks, townId));

        var foreignMarker = TownCivicState.empty().toTag();
        var facilities = foreignMarker.getList("Facilities", Tag.TAG_COMPOUND);
        facilities.add(new FacilityMarker(UUID.randomUUID(), FacilityType.TOWN_HALL, Level.OVERWORLD,
                BlockPos.ZERO, FacilityMarkerSide.BACK).toTag());
        assertThrows(IllegalArgumentException.class, () -> TownCivicState.fromTag(foreignMarker, townId));
    }
}
