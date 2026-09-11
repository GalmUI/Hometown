package dev.conner.hometown.room;

import dev.conner.hometown.housing.*;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.*;
import dev.conner.hometown.settlement.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivacyIntegrationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private List<BlockPos> positions(int count) {
        var beds=new ArrayList<BlockPos>();for(int i=0;i<count;i++)beds.add(new BlockPos(2+i*2,1,2));return beds;
    }
    private HousingSnapshot oneRoom(int population,int beds) {
        var positions=positions(beds);var room=new RoomDetectionResult(true,BlockPos.ZERO,100,Set.copyOf(positions),RoomFailureReason.NONE);
        return HousingScanner.aggregate(population,positions,p->room);
    }
    @Test void densityThresholdsAndScores() {
        int[] expected={100,80,60,60,40};
        for(int beds=1;beds<=5;beds++) {
            var h=oneRoom(beds,beds);assertEquals(expected[beds-1],h.privacyPercent().orElseThrow());
            assertEquals(100,h.capacityPercent().orElseThrow());assertEquals(beds,h.enclosedBeds());
            assertEquals(1,h.roomCount());assertEquals(beds==1?1:0,h.privateRooms());assertEquals(beds==2?1:0,h.sharedRooms());
            assertEquals(beds==3||beds==4?1:0,h.crowdedRooms());assertEquals(beds>=5?1:0,h.highDensityRooms());
            assertEquals(beds,h.privateBeds()+h.sharedBeds()+h.crowdedBeds()+h.highDensityBeds());
        }
    }
    @Test void sixPrivateAndTwoSharedProduceNinetyFive() {
        var beds=positions(8);Set<BlockPos> shared=Set.of(beds.get(6),beds.get(7));
        var h=HousingScanner.aggregate(10,beds,p->shared.contains(p)
                ? new RoomDetectionResult(true,beds.get(6).above(),20,shared,RoomFailureReason.NONE)
                : new RoomDetectionResult(true,p.above(),10,Set.of(p),RoomFailureReason.NONE));
        assertEquals(95,h.privacyPercent().orElseThrow());assertEquals(80,h.capacityPercent().orElseThrow());
        assertEquals(6,h.privateRooms());assertEquals(1,h.sharedRooms());assertEquals(7,h.roomCount());
        assertEquals(6,h.privateBeds());assertEquals(2,h.sharedBeds());
    }
    @Test void highDensityNeverReducesCapacity() {
        var h=oneRoom(10,10);assertEquals(100,h.capacityPercent().orElseThrow());assertEquals(40,h.privacyPercent().orElseThrow());
        assertEquals(HousingSnapshot.HousingCapacityState.SUFFICIENT,h.state());assertEquals(1,h.highDensityRooms());assertEquals(10,h.highDensityBeds());
    }
    @Test void shortageCanHavePerfectPrivacy() {
        var beds=positions(8);var h=HousingScanner.aggregate(10,beds,p->new RoomDetectionResult(true,p.above(),10,Set.of(p),RoomFailureReason.NONE));
        assertEquals(80,h.capacityPercent().orElseThrow());assertEquals(100,h.privacyPercent().orElseThrow());
        assertEquals(2,h.unhousedResidents());assertEquals(HousingSnapshot.HousingCapacityState.SHORTAGE,h.state());
    }
    @Test void realBlockBedAdditionAndRemovalRediscoverDensity() {
        var f=new RoomDetectorTest.Fixture();f.room(14,8,4,Blocks.STONE.defaultBlockState());
        var beds=positions(5);
        for(int count:new int[]{1,2,3,4,5,4,2,1}) {
            for(BlockPos p:beds) {f.blocks.put(p,Blocks.AIR.defaultBlockState());f.blocks.put(p.south(),Blocks.AIR.defaultBlockState());}
            var included=beds.subList(0,count);included.forEach(f::bed);
            var h=HousingScanner.aggregate(5,included,new RoomDetector(f.world)::detect);
            assertEquals(count,h.enclosedBeds());assertEquals(RoomDensity.forBeds(count).privacyPoints(),h.privacyPercent().orElseThrow());
        }
    }
    @Test void brokenWindowRemovesBedsFromPrivacyAndRepairRestoresThem() {
        var f=new RoomDetectorTest.Fixture();f.room(8,8,4,Blocks.STONE.defaultBlockState());var beds=positions(2);beds.forEach(f::bed);
        var before=HousingScanner.aggregate(2,beds,new RoomDetector(f.world)::detect);assertEquals(80,before.privacyPercent().orElseThrow());
        f.put(0,2,2,Blocks.AIR.defaultBlockState());
        var broken=HousingScanner.aggregate(2,beds,new RoomDetector(f.world)::detect);assertEquals(0,broken.enclosedBeds());
        assertEquals(0,broken.capacityPercent().orElseThrow());assertTrue(broken.privacyPercent().isEmpty());
        f.put(0,2,2,Blocks.GLASS.defaultBlockState());assertEquals(before,HousingScanner.aggregate(2,beds,new RoomDetector(f.world)::detect));
    }
    @Test void emptyAndZeroPopulationAndIncompleteHaveConsistentAvailability() {
        var empty=HousingScanner.aggregate(10,List.of(),p->{throw new AssertionError();});
        assertEquals(0,empty.capacityPercent().orElseThrow());assertTrue(empty.privacyPercent().isEmpty());
        assertEquals(HousingSnapshot.HousingCapacityState.SHORTAGE,empty.state());
        var unoccupied=oneRoom(0,2);assertTrue(unoccupied.capacityPercent().isEmpty());assertEquals(80,unoccupied.privacyPercent().orElseThrow());
        assertEquals(HousingSnapshot.HousingCapacityState.NO_RESIDENTS,unoccupied.state());
        var unavailable=HousingSnapshot.unavailable(10,8);assertTrue(unavailable.privacyPercent().isEmpty());assertTrue(unavailable.capacityPercent().isEmpty());
    }
    @Test void weightedPrivacyExcludesUnsealedBedsAndRounds() {
        var beds=positions(4);Set<BlockPos> shared=Set.of(beds.get(1),beds.get(2));
        var h=HousingScanner.aggregate(4,beds,p->p.equals(beds.get(3))
                ? new RoomDetectionResult(false,p,0,Set.of(),RoomFailureReason.ESCAPED_TO_OUTSIDE)
                : shared.contains(p)?new RoomDetectionResult(true,beds.get(1).above(),10,shared,RoomFailureReason.NONE)
                : new RoomDetectionResult(true,p.above(),10,Set.of(p),RoomFailureReason.NONE));
        assertEquals(87,h.privacyPercent().orElseThrow());assertEquals(75,h.capacityPercent().orElseThrow());assertEquals(1,h.unsealedBeds());
    }
    @Test void allDensityCategoriesAndUnavailablePrivacyRoundTrip() {
        var town=new Settlement(UUID.randomUUID(),"Test",Level.OVERWORLD,BlockPos.ZERO,8,UUID.randomUUID(),"Founder",0);
        var snapshots=new ArrayList<HousingSnapshot>();for(int count:new int[]{1,2,3,4,5,10})snapshots.add(oneRoom(10,count));
        snapshots.add(HousingSnapshot.unavailable(10,8));snapshots.add(HousingScanner.aggregate(10,List.of(),p->{throw new AssertionError();}));
        for(var h:snapshots) {
            var stats=new SettlementStats(h.population(),h.totalBeds(),0,0,SettlementStats.Availability.COMPLETE,List.of());
            var packet=new TownLedgerSnapshotPayload(1,TownLedgerSnapshot.of(town,stats,TownLedgerSnapshot.BellState.PRESENT,0).withHousing(h),TownLedgerSnapshotPayload.Error.NONE);
            var b=new FriendlyByteBuf(Unpooled.buffer());try {TownLedgerSnapshotPayload.STREAM_CODEC.encode(b,packet);assertEquals(packet,TownLedgerSnapshotPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        }
    }
}
