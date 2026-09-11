package dev.conner.hometown.settlement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IOUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettlementPersistenceTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private Settlement town(String name, BlockPos pos, int radius) {
        return new Settlement(UUID.randomUUID(), name, Level.OVERWORLD, pos, radius, UUID.randomUUID(), "Conner", 432000L);
    }

    @Test void persistsAllFieldsAcrossTwoDiskReloadsAndWorldCopy() throws Exception {
        HometownSavedData original = new HometownSavedData();
        Settlement oakridge = town("Oakridge", new BlockPos(152, 68, -341), 64);
        Settlement other = new Settlement(UUID.randomUUID(), "Moonrise", Level.NETHER, oakridge.bellPosition(), 96,
                UUID.randomUUID(), "Alex", 987654321L);
        original.addSettlement(oakridge);
        original.addSettlement(other);
        Path world = directory.resolve("hometown_settlements.dat");
        original.save(world.toFile(), null);
        // Pinned NeoForge queues SavedData writes; dirty=false does not mean disk I/O finished.
        IOUtilities.waitUntilIOWorkerComplete();
        assertFalse(original.isDirty());
        for (int reload = 0; reload < 2; reload++) {
            HometownSavedData loaded = HometownSavedData.load(NbtIo.readCompressed(world, NbtAccounter.unlimitedHeap()).getCompound("data"), null);
            assertEquals(oakridge, loaded.getSettlement(oakridge.id()).orElseThrow());
            assertEquals(other, loaded.getSettlement(other.id()).orElseThrow());
            assertEquals(18, loaded.getSettlement(oakridge.id()).orElseThrow().foundedDay());
            assertFalse(loaded.isDirty());
            loaded.setDirty();
            loaded.save(world.toFile(), null);
            IOUtilities.waitUntilIOWorkerComplete();
        }
        Path copy = directory.resolve("copied-world.dat");
        Files.copy(world, copy);
        var loadedCopy = HometownSavedData.load(NbtIo.readCompressed(copy, NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        assertEquals(oakridge, loadedCopy.findByName("OAKRIDGE").orElseThrow());
        assertEquals(96, loadedCopy.getSettlement(other.id()).orElseThrow().radius());
    }

    @Test void rejectsDuplicateNamesBellsAndIdsWithoutPartialWrites() {
        HometownSavedData data = new HometownSavedData();
        Settlement town = town("Oakridge", BlockPos.ZERO, 64);
        data.addSettlement(town);
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertThrows(IllegalArgumentException.class, () -> data.addSettlement(town("oAkRiDgE", new BlockPos(1000, 0, 0), 64)));
        assertThrows(IllegalArgumentException.class, () -> data.addSettlement(town("Other", BlockPos.ZERO, 64)));
        assertThrows(IllegalArgumentException.class, () -> data.addSettlement(town));
        assertEquals(1, data.all().size());
        assertFalse(data.isDirty());
        assertThrows(UnsupportedOperationException.class, () -> data.all().clear());
        assertEquals(town, data.removeSettlement(town.id()).orElseThrow());
        assertTrue(data.isDirty());
        var reloaded = HometownSavedData.load(data.save(new CompoundTag(), null), null);
        assertTrue(reloaded.all().isEmpty());
    }

    @Test void radiusAndDimensionQueriesUseStoredValuesAndHorizontalDistance() {
        Settlement town = town("Oakridge", BlockPos.ZERO, 64);
        assertTrue(town.overlaps(Level.OVERWORLD, new BlockPos(127, 1000, 0), 64));
        assertFalse(town.overlaps(Level.OVERWORLD, new BlockPos(128, 0, 0), 64));
        assertFalse(town.overlaps(Level.OVERWORLD, new BlockPos(91, 0, 91), 64));
        assertFalse(town.overlaps(Level.NETHER, BlockPos.ZERO, 64));
        assertTrue(town.overlaps(Level.OVERWORLD, new BlockPos(150, 0, 0), 96));
        assertTrue(town.contains(Level.OVERWORLD, new BlockPos(64, -100, 0)));
        assertFalse(town.contains(Level.OVERWORLD, new BlockPos(65, 0, 0)));
        HometownSavedData data = new HometownSavedData();
        data.addSettlement(town);
        assertEquals(town, data.findByBell(Level.OVERWORLD, BlockPos.ZERO).orElseThrow());
        assertTrue(data.findByBell(Level.NETHER, BlockPos.ZERO).isEmpty());
        assertEquals(town, data.findSettlementContaining(Level.OVERWORLD, new BlockPos(0, 100, 0)).orElseThrow());
        assertEquals(1, data.getSettlementsInDimension(Level.OVERWORLD).size());
        assertTrue(data.getSettlementsInDimension(Level.END).isEmpty());
    }

    @Test void malformedRecordsFailVisibly() {
        assertThrows(IllegalStateException.class, () -> HometownSavedData.load(new CompoundTag(), null));
        var invalid = town("Oakridge", BlockPos.ZERO, 64).toTag();
        invalid.remove("Id");
        assertThrows(IllegalArgumentException.class, () -> Settlement.fromTag(invalid));
    }
}
