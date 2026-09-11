package dev.conner.hometown.room;

import dev.conner.hometown.housing.*;
import dev.conner.hometown.settlement.*;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HousingIntegrationTest {
    private static final BlockPos BED=new BlockPos(2,1,2);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private RoomDetectorTest.Fixture room() {
        var f=new RoomDetectorTest.Fixture(); f.room(10,10,4,Blocks.STONE.defaultBlockState()); return f;
    }
    private HousingSnapshot aggregate(RoomDetectorTest.Fixture f, int population, BlockPos... beds) {
        return HousingScanner.aggregate(population,List.of(beds),new RoomDetector(f.world)::detect);
    }
    private RoomDetectionResult single(BlockPos p) { return new RoomDetectionResult(true,p.above(),1,Set.of(p),RoomFailureReason.NONE); }
    @Test void baselineAddRemoveAndPopulationChanges() {
        var beds=new ArrayList<BlockPos>(); for(int i=0;i<9;i++)beds.add(new BlockPos(i,1,1));
        var baseline=HousingScanner.aggregate(10,beds,this::single);
        assertEquals(90,baseline.capacityPercent().orElseThrow()); assertEquals(1,baseline.unhousedResidents());
        assertEquals(HousingSnapshot.HousingCapacityState.SHORTAGE,baseline.state());
        beds.add(new BlockPos(10,1,1)); var enough=HousingScanner.aggregate(10,beds,this::single);
        assertEquals(100,enough.capacityPercent().orElseThrow()); assertEquals(0,enough.unhousedResidents());
        assertEquals(HousingSnapshot.HousingCapacityState.SUFFICIENT,enough.state());
        beds.removeLast(); assertEquals(baseline,HousingScanner.aggregate(10,beds,this::single));
        assertEquals(2,HousingScanner.aggregate(11,beds,this::single).unhousedResidents());
        assertEquals(1,HousingScanner.aggregate(8,beds,this::single).spareHousingCapacity());
    }
    @Test void windowBreakRepairAndDoorStateRefresh() {
        var f=room(); var before=aggregate(f,1,BED); assertEquals(1,before.enclosedBeds());
        f.put(0,2,2,Blocks.AIR.defaultBlockState()); var broken=aggregate(f,1,BED);
        assertEquals(before.totalBeds(),broken.totalBeds()); assertEquals(0,broken.enclosedBeds());assertEquals(1,broken.unsealedBeds());
        f.put(0,2,2,Blocks.GLASS.defaultBlockState()); assertEquals(before,aggregate(f,1,BED));
        for(boolean open:new boolean[]{true,false}) {
            f.put(0,2,2,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,open));
            assertEquals(before,aggregate(f,1,BED));
        }
    }
    @Test void sharedBedsDeduplicateRoomAndReuseFill() {
        var f=room();var second=new BlockPos(4,1,2);f.bed(second);var calls=new AtomicInteger();var detector=new RoomDetector(f.world);
        var h=HousingScanner.aggregate(2,List.of(BED,second),p->{calls.incrementAndGet();return detector.detect(p);});
        assertEquals(1,calls.get());assertEquals(2,h.enclosedBeds());assertEquals(1,h.roomCount());
        assertEquals(0,h.privateRooms());assertEquals(1,h.sharedRooms());assertEquals(2,h.sharedBeds());
        assertEquals(HousingSnapshot.HousingCapacityState.SUFFICIENT,h.state());
    }
    @Test void separateBedroomsArePrivate() {
        var f=room();var second=new BlockPos(7,1,2);f.bed(second);
        for(int y=1;y<4;y++)for(int z=1;z<10;z++)f.put(5,y,z,Blocks.STONE.defaultBlockState());
        var h=aggregate(f,2,BED,second);assertEquals(2,h.roomCount());assertEquals(2,h.privateRooms());assertEquals(0,h.sharedRooms());
    }
    @Test void outOfTownRoomBedsDoNotInflateCapacity() {
        var f=room();f.bed(new BlockPos(4,1,2));var h=aggregate(f,2,BED);
        assertEquals(1,h.totalBeds());assertEquals(1,h.enclosedBeds());assertEquals(1,h.sharedRooms());assertEquals(1,h.sharedBeds());
    }
    @Test void outdoorBedAddsNoCapacity() {
        var f=room();var outdoor=new BlockPos(12,1,2);f.bed(outdoor);var h=aggregate(f,2,BED,outdoor);
        assertEquals(2,h.totalBeds());assertEquals(1,h.enclosedBeds());assertEquals(1,h.unsealedBeds());assertEquals(1,h.unhousedResidents());
    }
    @Test void zeroPopulationAndSurplusAndRounding() {
        var h=HousingScanner.aggregate(0,List.of(BED),this::single);assertTrue(h.capacityPercent().isEmpty());
        assertEquals(HousingSnapshot.HousingCapacityState.NO_RESIDENTS,h.state());assertEquals(1,h.spareHousingCapacity());
        assertEquals(100,HousingScanner.aggregate(1,List.of(BED,BED.east()),this::single).capacityPercent().orElseThrow());
        assertEquals(33,HousingScanner.aggregate(3,List.of(BED),this::single).capacityPercent().orElseThrow());
    }
    @Test void unavailableRoomIsUnknownNotUnsealed() {
        var h=HousingScanner.aggregate(2,List.of(BED),p->new RoomDetectionResult(false,p,0,Set.of(),RoomFailureReason.CHUNK_UNAVAILABLE));
        assertFalse(h.scanComplete());assertEquals(1,h.unknownBeds());assertEquals(0,h.unsealedBeds());assertTrue(h.capacityPercent().isEmpty());
        assertEquals(HousingSnapshot.HousingCapacityState.SCAN_INCOMPLETE,h.state());
    }
    @Test void aggregationBudgetStopsNewFills() {
        var beds=new ArrayList<BlockPos>();for(int i=0;i<100;i++)beds.add(new BlockPos(i,1,1));var calls=new AtomicInteger();
        var h=HousingScanner.aggregate(100,beds,p->{calls.incrementAndGet();return single(p);});
        assertEquals(HousingScanner.MAX_ROOM_ATTEMPTS,calls.get());assertFalse(h.scanComplete());assertEquals(68,h.unknownBeds());assertEquals(0,h.unsealedBeds());
    }
    @Test void volumeFailureIsUnsealedButSessionBudgetIsUnknown() {
        for(var reason:List.of(RoomFailureReason.MAX_VOLUME_EXCEEDED,RoomFailureReason.SCAN_BUDGET_EXCEEDED)) {
            var h=HousingScanner.aggregate(1,List.of(BED),p->new RoomDetectionResult(false,p,0,Set.of(),reason));
            assertEquals(reason==RoomFailureReason.MAX_VOLUME_EXCEEDED,h.scanComplete());
            assertEquals(reason==RoomFailureReason.MAX_VOLUME_EXCEEDED?1:0,h.unsealedBeds());
        }
    }
    @Test void laterEnclosedResultResolvesEarlierLimitedMember() {
        BlockPos second=BED.east();
        var h=HousingScanner.aggregate(2,List.of(BED,second),p->p.equals(BED)
                ? new RoomDetectionResult(false,p,32,Set.of(),RoomFailureReason.MAX_DISTANCE_EXCEEDED)
                : new RoomDetectionResult(true,BED.above(),60,Set.of(BED,second),RoomFailureReason.NONE));
        assertEquals(2,h.enclosedBeds());assertEquals(0,h.unsealedBeds());assertEquals(1,h.roomCount());
    }
    private Settlement town() {return new Settlement(UUID.randomUUID(),"Test",Level.OVERWORLD,new BlockPos(5,1,5),8,UUID.randomUUID(),"Founder",0);}
    @Test void serverScanUsesOnlyRelevantIntactVanillaHomePois() {
        var f=room(); var pois=mock(PoiManager.class);when(f.level.getPoiManager()).thenReturn(pois);
        when(f.level.getBlockState(any(BlockPos.class))).thenAnswer(c->f.state(c.getArgument(0)));
        var record=mock(PoiRecord.class);when(record.getPos()).thenReturn(BED);
        when(pois.getInChunk(any(),any(),eq(PoiManager.Occupancy.ANY))).thenAnswer(c->{ChunkPos chunk=c.getArgument(1);return chunk.x==0&&chunk.z==0?List.of(record).stream():java.util.stream.Stream.empty();});
        var stats=new SettlementStats(2,1,0,0,SettlementStats.Availability.COMPLETE,List.of());
        var h=HousingScanner.scan(f.level,town(),stats,8);assertEquals(1,h.enclosedBeds());assertEquals(1,h.unhousedResidents());
        // Breaking the bed leaves a stale POI; the existing validity check rejects it.
        f.put(2,1,2,Blocks.AIR.defaultBlockState());
        var after=HousingScanner.scan(f.level,town(),new SettlementStats(2,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),8);
        assertTrue(after.scanComplete());assertEquals(0,after.totalBeds());
    }
    @Test void bedHalfBeyondTownEdgeInMissingChunkMakesScanIncomplete() {
        var f=room(); f.missingChunkX.add(1);
        var head=new BlockPos(15,1,2);
        f.blocks.put(head,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                .setValue(BedBlock.FACING,net.minecraft.core.Direction.WEST));
        when(f.level.getBlockState(any(BlockPos.class))).thenAnswer(c->f.state(c.getArgument(0)));
        var pois=mock(PoiManager.class);when(f.level.getPoiManager()).thenReturn(pois);
        var record=mock(PoiRecord.class);when(record.getPos()).thenReturn(head);
        when(pois.getInChunk(any(),any(),eq(PoiManager.Occupancy.ANY))).thenAnswer(c->{ChunkPos chunk=c.getArgument(1);return chunk.x==0&&chunk.z==0?List.of(record).stream():java.util.stream.Stream.empty();});
        var town=new Settlement(UUID.randomUUID(),"Edge",Level.OVERWORLD,new BlockPos(5,1,5),10,UUID.randomUUID(),"Founder",0);
        var h=HousingScanner.scan(f.level,town,new SettlementStats(1,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),8);
        assertFalse(h.scanComplete());assertEquals(0,h.unsealedBeds());
        assertTrue(mockingDetails(f.source).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("getChunkNow")));
    }
    @Test void incompleteSettlementSkipsRoomWorkAndRetainsUnknownCounts() {
        var f=room();var h=HousingScanner.scan(f.level,town(),new SettlementStats(2,3,0,0,SettlementStats.Availability.PARTIAL,List.of()),8);
        assertFalse(h.scanComplete());assertEquals(3,h.unknownBeds());assertEquals(0,h.unsealedBeds());verifyNoInteractions(f.source);
    }
    @Test void housingPayloadRoundTripsAllStates() {
        var town=town();var stats=new SettlementStats(2,1,0,0,SettlementStats.Availability.COMPLETE,List.of());
        for(var h:List.of(HousingScanner.aggregate(2,List.of(BED),this::single),HousingScanner.aggregate(0,List.of(BED),this::single),HousingSnapshot.unavailable(2,1))) {
            var packet=new TownLedgerSnapshotPayload(1,TownLedgerSnapshot.of(town,stats,TownLedgerSnapshot.BellState.PRESENT,0).withHousing(h),TownLedgerSnapshotPayload.Error.NONE);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());try {
                TownLedgerSnapshotPayload.STREAM_CODEC.encode(buffer,packet);assertEquals(packet,TownLedgerSnapshotPayload.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());
            } finally {buffer.release();}
        }
    }
}
