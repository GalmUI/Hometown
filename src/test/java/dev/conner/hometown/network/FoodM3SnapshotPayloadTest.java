package dev.conner.hometown.network;

import dev.conner.hometown.food.*;
import dev.conner.hometown.observation.ObservationMetadata;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodM3SnapshotPayloadTest {
    @Test void roundTripPreservesVarietyGrowingAndUnknownMaturity(){
        var meta=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",7,120,1,2,3);
        var totals=new EnumMap<FoodGroup,Long>(FoodGroup.class);var q=new EnumMap<FoodGroup,FoodVarietySnapshot.Qualification>(FoodGroup.class);
        for(var group:FoodGroup.values()){totals.put(group,group==FoodGroup.GRAINS?20L:0L);q.put(group,group==FoodGroup.GRAINS?FoodVarietySnapshot.Qualification.TRUE:FoodVarietySnapshot.Qualification.UNKNOWN);}
        var variety=new FoodVarietySnapshot(meta,true,10,true,totals,EnumSet.allOf(FoodGroup.class),OptionalLong.of(20),q,2,7,OptionalDouble.of(20),OptionalDouble.empty(),FoodVarietySnapshot.VarietyState.INCOMPLETE,1,FoodScanStatus.PARTIAL);
        var families=List.of(new FoodGrowingSnapshot.FamilyRecord(ResourceLocation.parse("example:berries"),3,OptionalInt.empty(),FoodGrowingSnapshot.MaturityStatus.UNASSESSED));
        var growing=new FoodGrowingSnapshot(meta,true,FoodGrowingSnapshot.Status.COMPLETE,Map.of(FoodGrowingSnapshot.Reason.MATURITY_UNASSESSED,3),3,0,3,1,families,1,1,1,2,20,22,262144);
        var original=new FoodM3SnapshotPayload(42,variety,growing);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        FoodM3SnapshotPayload.STREAM_CODEC.encode(buffer,original);var decoded=FoodM3SnapshotPayload.STREAM_CODEC.decode(buffer);
        assertEquals(original,decoded);assertTrue(decoded.growing().families().getFirst().mature().isEmpty());
    }
}
