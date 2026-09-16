package dev.conner.hometown.civic;

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

class NoticeBoardFoundationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void noticeBoardSignGrammarIsNarrowButCaseAndWhitespaceFriendly() {
        assertTrue(NoticeBoardSignGrammar.matches("[Hometown]", "Notice Board"));
        assertTrue(NoticeBoardSignGrammar.matches("  [HOMETOWN] ", "  notice   board  "));
        assertFalse(NoticeBoardSignGrammar.matches("Hometown", "Notice Board"));
        assertFalse(NoticeBoardSignGrammar.matches("[Hometown]", "Board"));
        assertFalse(NoticeBoardSignGrammar.matches("[Hometown]", "Town Hall"));
    }

    @Test void firstNoticeBoardPersistsAndReplacementKeepsOneMarker() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);

        FacilityMarker first = new FacilityMarker(id, FacilityType.NOTICE_BOARD, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);
        data.setDirty(false);
        var firstCommit = NoticeBoardService.commit(data, town, first);
        assertTrue(firstCommit.changed());
        assertTrue(firstCommit.firstEstablishment());
        assertTrue(data.isDirty());
        assertEquals(first, data.civicState(id).facility(FacilityType.NOTICE_BOARD).orElseThrow());

        data.setDirty(false);
        var duplicate = NoticeBoardService.commit(data, town, first);
        assertFalse(duplicate.changed());
        assertFalse(duplicate.firstEstablishment());
        assertFalse(data.isDirty());

        FacilityMarker replacement = new FacilityMarker(id, FacilityType.NOTICE_BOARD, Level.OVERWORLD,
                new BlockPos(20, 64, 20), FacilityMarkerSide.BACK);
        var replacementCommit = NoticeBoardService.commit(data, town, replacement);
        assertTrue(replacementCommit.changed());
        assertFalse(replacementCommit.firstEstablishment());
        assertEquals(replacement, data.civicState(id).facility(FacilityType.NOTICE_BOARD).orElseThrow());

        CompoundTag saved = data.save(new CompoundTag(), null);
        HometownSavedData loaded = HometownSavedData.load(saved, null);
        assertEquals(replacement, loaded.civicState(id).facility(FacilityType.NOTICE_BOARD).orElseThrow());
    }

    @Test void noticeBoardCannotBeCommittedBeforeTownHallUnlocksIt() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        FacilityMarker board = new FacilityMarker(id, FacilityType.NOTICE_BOARD, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT);

        assertThrows(IllegalStateException.class, () -> NoticeBoardService.commit(data, town, board));
        assertTrue(data.civicState(id).facility(FacilityType.NOTICE_BOARD).isEmpty());
    }

    @Test void administrationDistinguishesNoticeBoardUnlockFromEstablishmentAndValidity() {
        HometownSavedData data = new HometownSavedData();
        UUID id = UUID.randomUUID();
        Settlement town = new Settlement(id, "Osea", Level.OVERWORLD, BlockPos.ZERO, 64,
                UUID.randomUUID(), "Founder", 0L);
        data.addSettlement(town, DyeColor.BLUE, DyeColor.WHITE);
        data.registerTownHall(id, new FacilityMarker(id, FacilityType.TOWN_HALL, Level.OVERWORLD,
                new BlockPos(4, 64, 4), FacilityMarkerSide.FRONT), 24000L);

        var unlockedOnly = TownAdministrationService.snapshotFor(town, data.civicState(id));
        assertTrue(unlockedOnly.noticeBoardUnlocked());
        assertFalse(unlockedOnly.noticeBoardEstablished());
        assertFalse(unlockedOnly.noticeBoardActive());

        NoticeBoardService.commit(data, town, new FacilityMarker(id, FacilityType.NOTICE_BOARD, Level.OVERWORLD,
                new BlockPos(12, 64, 12), FacilityMarkerSide.FRONT));
        var active = TownAdministrationService.snapshotFor(town, data.civicState(id), true, false, false);
        assertTrue(active.noticeBoardUnlocked());
        assertTrue(active.noticeBoardEstablished());
        assertTrue(active.noticeBoardActive());
    }
}
