package dev.conner.hometown.settlement;

import dev.conner.hometown.commerce.*;
import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementCommerceObservationTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    @Test void E08_existingResidentQueryProducesDedupedCommerceFactsWithoutSecondScan(){
        var level=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var pois=mock(PoiManager.class);
        when(level.getChunkSource()).thenReturn(chunks);when(level.getPoiManager()).thenReturn(pois);when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(mock(LevelChunk.class));
        when(pois.getInChunk(any(),any(),eq(PoiManager.Occupancy.ANY))).thenReturn(Stream.empty());
        var farmer=villager(new UUID(0,1),VillagerProfession.FARMER,false);var unemployed=villager(new UUID(0,2),VillagerProfession.NONE,false);
        when(level.getEntitiesOfClass(eq(Villager.class),any(),any())).thenReturn(List.of(farmer,farmer,unemployed));
        var town=new Settlement(UUID.randomUUID(),"Commerce",Level.OVERWORLD,new BlockPos(8,64,8),4,UUID.randomUUID(),"Founder",0);
        var observation=SettlementScanner.observe(level,town,32);
        assertEquals(2,observation.stats().population());assertEquals(2,observation.residentFacts().size());assertEquals(3,observation.residentInspections());assertEquals(1,observation.duplicateResidents());
        var commerce=CommerceEvaluator.evaluate(new ObservationMetadata(town.id(),town.dimension().location().toString(),1,0,0,0,0),observation,true);
        assertEquals(2,commerce.eligibleAdults());assertEquals(1,commerce.employedAdults());assertEquals(50.0,commerce.employmentPercent().orElseThrow());
        verify(level,times(1)).getEntitiesOfClass(eq(Villager.class),any(),any());
        verify(farmer,never()).getOffers();verify(unemployed,never()).getOffers();
    }

    private static Villager villager(UUID id,VillagerProfession profession,boolean baby){
        var v=mock(Villager.class);when(v.getUUID()).thenReturn(id);when(v.isAlive()).thenReturn(true);when(v.isBaby()).thenReturn(baby);
        when(v.position()).thenReturn(new Vec3(8,64,8));when(v.blockPosition()).thenReturn(new BlockPos(8,64,8));
        when(v.getVillagerData()).thenReturn(new VillagerData(VillagerType.PLAINS,profession,1));return v;
    }
}
