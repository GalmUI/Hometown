package dev.conner.hometown.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementScannerTest {
    private ServerLevel level;
    private ServerChunkCache chunks;
    private PoiManager pois;
    private final List<Villager> villagers = new ArrayList<>();
    private final List<PoiRecord> beds = new ArrayList<>();
    private final Settlement town = new Settlement(UUID.randomUUID(), "Dured", Level.OVERWORLD,
            new BlockPos(8, 64, 8), 16, UUID.randomUUID(), "GalrUI", 432000);

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        level = mock(ServerLevel.class);
        chunks = mock(ServerChunkCache.class);
        pois = mock(PoiManager.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(level.getPoiManager()).thenReturn(pois);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        when(level.getEntitiesOfClass(eq(Villager.class), any(), any())).thenAnswer(call -> {
            java.util.function.Predicate<Villager> predicate = call.getArgument(2);
            return villagers.stream().filter(predicate).toList();
        });
        when(pois.getInChunk(any(), any(), eq(PoiManager.Occupancy.ANY))).thenAnswer(call -> {
            net.minecraft.world.level.ChunkPos pos = call.getArgument(1);
            return pos.equals(new net.minecraft.world.level.ChunkPos(0, 0)) ? beds.stream() : Stream.empty();
        });
    }

    private Villager resident(int index, VillagerProfession profession, boolean baby, String name) {
        Villager v = mock(Villager.class);
        when(v.getUUID()).thenReturn(new UUID(0, index));
        doReturn(EntityType.VILLAGER).when(v).getType();
        when(v.isAlive()).thenReturn(true);
        when(v.isBaby()).thenReturn(baby);
        when(v.position()).thenReturn(new Vec3(8, 64, 8));
        when(v.blockPosition()).thenReturn(new BlockPos(8, 64, 8));
        when(v.getVillagerData()).thenReturn(new VillagerData(VillagerType.PLAINS, profession, 1));
        if (name != null) when(v.getCustomName()).thenReturn(Component.literal(name));
        villagers.add(v);
        return v;
    }
    private void bed(int x) {
        BlockPos pos = new BlockPos(x, 64, 4);
        PoiRecord record = mock(PoiRecord.class);
        when(record.getPos()).thenReturn(pos);
        var head = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
        when(level.getBlockState(pos)).thenReturn(head);
        when(level.getBlockState(pos.relative(head.getValue(BedBlock.FACING).getOpposite())))
                .thenReturn(head.setValue(BedBlock.PART, BedPart.FOOT));
        beds.add(record);
    }

    @Test void reopeningReflectsAddedResidentsBedsAndVanillaProfessionChanges() {
        Villager farmer = resident(1, VillagerProfession.FARMER, false, "Gerald");
        resident(2, VillagerProfession.NONE, false, null);
        bed(2); bed(4);
        var first = SettlementScanner.scan(level, town, 32);
        assertEquals(List.of(2, 2, 1, 1), List.of(first.population(), first.beds(), first.employed(), first.professionDiversity()));
        resident(3, VillagerProfession.LIBRARIAN, false, "Mira"); bed(6);
        var second = SettlementScanner.scan(level, town, 32);
        assertEquals(List.of(3, 3, 2, 2), List.of(second.population(), second.beds(), second.employed(), second.professionDiversity()));
        when(farmer.getVillagerData()).thenReturn(new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
        var third = SettlementScanner.scan(level, town, 32);
        assertEquals(List.of(3, 3, 1, 1), List.of(third.population(), third.beds(), third.employed(), third.professionDiversity()));
        assertEquals("hometown.ledger.unemployed", third.residents().getFirst().professionKey());
        assertEquals(1, first.employed()); // Prior snapshots never mutate with the world.
    }

    @Test void countsBabiesButExcludesThemAndNitwitsFromEmploymentAndDiversity() {
        resident(1, VillagerProfession.FARMER, false, "Gerald");
        resident(2, VillagerProfession.FARMER, false, null);
        resident(3, VillagerProfession.LIBRARIAN, false, "Mira");
        resident(4, VillagerProfession.NITWIT, false, null);
        resident(5, VillagerProfession.ARMORER, true, "Little One");
        var stats = SettlementScanner.scan(level, town, 32);
        assertEquals(5, stats.population()); assertEquals(3, stats.employed()); assertEquals(2, stats.professionDiversity());
        assertEquals("Gerald", stats.residents().getFirst().name());
        assertTrue(stats.residents().get(4).child());
        assertEquals("", stats.residents().get(1).name());
        assertThrows(UnsupportedOperationException.class, () -> stats.residents().clear());
    }

    @Test void storedRadiusVerticalBoundsAndLifeStateLimitResidents() {
        resident(1, VillagerProfession.FARMER, false, null);
        Villager outside = resident(2, VillagerProfession.FARMER, false, null);
        when(outside.position()).thenReturn(new Vec3(25, 64, 8));
        Villager above = resident(3, VillagerProfession.FARMER, false, null);
        when(above.position()).thenReturn(new Vec3(8, 98, 8));
        Villager dead = resident(4, VillagerProfession.FARMER, false, null);
        when(dead.isAlive()).thenReturn(false);
        assertEquals(1, SettlementScanner.scan(level, town, 32).population());
        assertEquals(2, SettlementScanner.scan(level, town, 40).population());
    }

    @Test void partialAndUnavailableStatesDoNotForceLoadChunks() {
        resident(1, VillagerProfession.NONE, true, null);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        when(chunks.getChunkNow(0, 0)).thenReturn(mock(LevelChunk.class));
        var partial = SettlementScanner.scan(level, town, 32);
        assertEquals(SettlementStats.Availability.PARTIAL, partial.availability());
        assertEquals(1, partial.population());
        verify(pois, times(1)).getInChunk(any(), eq(new net.minecraft.world.level.ChunkPos(0, 0)), any());
        when(chunks.getChunkNow(0, 0)).thenReturn(null);
        assertEquals(SettlementStats.Availability.UNAVAILABLE, SettlementScanner.scan(level, town, 32).availability());
        assertEquals(SettlementStats.Availability.UNAVAILABLE, SettlementScanner.scan(null, town, 32).availability());
        verify(chunks, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
    }
}
