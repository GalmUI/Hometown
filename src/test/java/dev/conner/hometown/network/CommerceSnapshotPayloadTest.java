package dev.conner.hometown.network;

import dev.conner.hometown.commerce.CommerceSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommerceSnapshotPayloadTest {
    @Test void roundTripPreservesAuthoritativeCommerceObservation(){
        var meta=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",9,240,1,2,3);
        var professions=Map.of(ResourceLocation.parse("minecraft:farmer"),3,ResourceLocation.parse("example:engineer"),2);
        var snapshot=new CommerceSnapshot(meta,true,CommerceSnapshot.Status.COMPLETE,Map.of(),7,6,5,1,0,1,
                OptionalDouble.of(100.0*5/6),OptionalDouble.empty(),CommerceSnapshot.EmploymentState.STRONG,2,professions,7,0);
        var original=new CommerceSnapshotPayload(44,snapshot);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        CommerceSnapshotPayload.STREAM_CODEC.encode(buffer,original);var decoded=CommerceSnapshotPayload.STREAM_CODEC.decode(buffer);
        assertEquals(original,decoded);assertEquals(5,decoded.commerce().employedAdults());assertEquals(2,decoded.commerce().professionDiversity());
    }
}
