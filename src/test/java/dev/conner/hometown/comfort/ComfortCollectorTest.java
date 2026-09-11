package dev.conner.hometown.comfort;

import dev.conner.hometown.housing.*;
import dev.conner.hometown.observation.*;
import dev.conner.hometown.room.RoomGeometry;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static dev.conner.hometown.comfort.ComfortSnapshot.*;

class ComfortCollectorTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    static class World {
        final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache source=mock(ServerChunkCache.class);
        final LevelChunk chunk=mock(LevelChunk.class);
        final Map<BlockPos,BlockState> states=new HashMap<>();
        final Set<String> unloaded=new HashSet<>();
        final AABB bounds=new AABB(-8,56,-8,9,73,9);
        World(){
            when(level.getChunkSource()).thenReturn(source);
            when(source.getChunkNow(anyInt(),anyInt())).thenAnswer(c->unloaded.contains(c.getArgument(0)+":"+c.getArgument(1))?null:chunk);
            when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(c->states.getOrDefault(c.getArgument(0),Blocks.AIR.defaultBlockState()));
        }
        BlockState tagged(Block block,ComfortCategory... categories){
            var state=spy(block.defaultBlockState());
            for(var category:categories)doReturn(true).when(state).is(category.tag);
            doReturn(false).when(state).is(ComfortCollector.EXCLUDED);
            return state;
        }
        BlockState excluded(Block block,ComfortCategory... categories){
            var state=tagged(block,categories);doReturn(true).when(state).is(ComfortCollector.EXCLUDED);return state;
        }
        void put(BlockPos pos,BlockState state){states.put(pos,state);}
        void noLoads(){assertTrue(mockingDetails(source).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("getChunkNow")));}
    }

    private ComfortSettings settings(int rooms,int cells){
        var categories=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values())categories.put(c,new ComfortSettings.Category(c.defaultEnabled,c.defaultWeight));
        return new ComfortSettings(true,categories,rooms,cells);
    }
    private ObservationMetadata metadata(){return new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",4,123,5,6,7);}
    private HousingScanner.Observation housing(List<RoomGeometry> rooms,boolean complete){
        var beds=rooms.stream().flatMap(r->r.beds().stream()).collect(java.util.stream.Collectors.toSet());
        var snapshot=complete?new HousingSnapshot(4,beds.size(),beds.size(),0,rooms.size(),rooms.size(),0,0,0,true)
                :HousingSnapshot.unavailable(4,beds.size());
        return new HousingScanner.Observation(snapshot,beds,rooms);
    }
    private RoomGeometry room(BlockPos key,Set<BlockPos> beds,Set<BlockPos> interior,Set<BlockPos> boundary,boolean complete){
        return new RoomGeometry(key,beds,interior,boundary,complete);
    }
    private ComfortSnapshot collect(World w,HousingScanner.Observation housing,ComfortSettings settings,int shared){
        return ComfortCollector.collect(housing,metadata(),settings,new ComfortRules.Definitions(Map.of()),
                new BlockObservationCache(w.level,w.bounds,shared));
    }

    @Test void C05_onlyProvenInteriorAndFirstBoundaryCountAndSharedWallCountsPerRoom(){
        var w=new World();var shared=new BlockPos(1,64,0);var leak=new BlockPos(2,64,0);
        var a=room(new BlockPos(0,64,0),Set.of(new BlockPos(0,64,0)),Set.of(new BlockPos(0,64,0)),Set.of(shared),true);
        var b=room(new BlockPos(1,64,1),Set.of(new BlockPos(1,64,1)),Set.of(new BlockPos(1,64,1)),Set.of(shared),true);
        w.put(shared,w.tagged(Blocks.CHEST,ComfortCategory.STORAGE));
        w.put(leak,w.tagged(Blocks.BOOKSHELF,ComfortCategory.BOOKS)); // behind boundary, not proven geometry
        var s=collect(w,housing(List.of(a,b),true),settings(10,20),20);
        assertEquals(Status.COMPLETE,s.scanStatus());assertEquals(2,s.assessedRooms());
        assertEquals(2,s.categoryCoverage().get(ComfortCategory.STORAGE));
        assertEquals(0,s.categoryCoverage().get(ComfortCategory.BOOKS));
        assertEquals(3,s.blockInspections(),"shared physical boundary read is cached once");
        assertTrue(s.roomRecords().stream().allMatch(r->r.perCategoryQualifyingHitCount().get(ComfortCategory.STORAGE)==1));
        verify(w.chunk,never()).getBlockState(leak);w.noLoads();
    }

    @Test void C07_exclusionWinsBeforeMultiCategoryRecognition(){
        var w=new World();var pos=new BlockPos(0,64,0);
        w.put(pos,w.excluded(Blocks.CHEST,ComfortCategory.STORAGE,ComfortCategory.BOOKS));
        var r=room(pos,Set.of(pos),Set.of(pos),Set.of(),true);
        var s=collect(w,housing(List.of(r),true),settings(10,20),20);var record=s.roomRecords().getFirst();
        assertEquals(1,record.excludedBlocks());assertEquals(0,record.presentWeight());
        assertEquals(Presence.FALSE,record.categoryPresence().get(ComfortCategory.STORAGE));
        assertEquals(Presence.FALSE,record.categoryPresence().get(ComfortCategory.BOOKS));
    }

    @Test void Q03_perRoomAndSharedLimitsRetainKnownFactsAndDistinguishNoWork(){
        var w=new World();var first=new BlockPos(0,64,0),second=new BlockPos(1,64,0);
        w.put(first,w.tagged(Blocks.CHEST,ComfortCategory.STORAGE));w.put(second,Blocks.AIR.defaultBlockState());
        var r=room(first,Set.of(first),Set.of(first,second),Set.of(),true);
        var partial=collect(w,housing(List.of(r),true),settings(10,1),20);
        assertEquals(Status.PARTIAL,partial.scanStatus());assertEquals(1,partial.blockInspections());
        assertEquals(Presence.TRUE,partial.roomRecords().getFirst().categoryPresence().get(ComfortCategory.STORAGE));
        assertTrue(partial.reasonCounts().containsKey(Reason.SCAN_LIMIT_REACHED));

        var none=collect(new World(),housing(List.of(r),true),settings(10,20),0);
        assertEquals(Status.UNAVAILABLE,none.scanStatus());assertEquals(0,none.blockInspections());
        assertTrue(none.reasonCounts().containsKey(Reason.SCAN_LIMIT_REACHED));

        var emptyWorld=new World();var empty=collect(emptyWorld,housing(List.of(r),true),settings(10,20),20);
        assertEquals(Status.COMPLETE,empty.scanStatus());assertEquals(0,empty.residentialComfortPercent().orElseThrow());
    }

    @Test void Q05_outOfBoundsBoundaryIsPartialAndCannotContribute(){
        var w=new World();var inside=new BlockPos(0,64,0),outside=new BlockPos(9,64,0);
        w.put(outside,w.tagged(Blocks.CHEST,ComfortCategory.STORAGE));
        var r=room(inside,Set.of(inside),Set.of(inside),Set.of(outside),true);
        var s=collect(w,housing(List.of(r),true),settings(10,20),20);
        assertEquals(Status.PARTIAL,s.scanStatus());assertTrue(s.reasonCounts().containsKey(Reason.SAMPLE_OUT_OF_BOUNDS));
        assertEquals(Presence.UNKNOWN,s.roomRecords().getFirst().categoryPresence().get(ComfortCategory.STORAGE));
        assertTrue(s.residentialComfortPercent().isEmpty());assertTrue(s.observedRoomComfortPercent().isEmpty());w.noLoads();
    }

    @Test void C08_Q04_Q06_incompleteHousingKeepsRoomEvidenceWithoutChangingHousingOrLoadingChunks(){
        var w=new World();var pos=new BlockPos(0,64,0);w.put(pos,w.tagged(Blocks.CHEST,ComfortCategory.STORAGE));
        var r=room(pos,Set.of(pos),Set.of(pos),Set.of(),true);var h=housing(List.of(r),false);var before=h.snapshot();
        var s=collect(w,h,settings(10,20),20);
        assertEquals(Status.PARTIAL,s.scanStatus());assertEquals(Condition.INCOMPLETE,s.condition());
        assertTrue(s.residentialComfortPercent().isEmpty());assertTrue(s.observedRoomComfortPercent().isPresent());
        assertEquals(Presence.TRUE,s.roomRecords().getFirst().categoryPresence().get(ComfortCategory.STORAGE));
        assertEquals(before,h.snapshot(),"Comfort must not mutate Housing");w.noLoads();
    }
}
