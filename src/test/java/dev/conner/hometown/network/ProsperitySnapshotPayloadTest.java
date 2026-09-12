package dev.conner.hometown.network;

import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.prosperity.ProsperitySnapshot;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProsperitySnapshotPayloadTest {
    @Test void roundTripPreservesFiveInputDevelopmentIndex(){
        var meta=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",17,800,11,22,33);
        var components=new ArrayList<ProsperitySnapshot.Component>();
        for(var type:ProsperitySnapshot.ComponentType.values())components.add(new ProsperitySnapshot.Component(type,
                ProsperitySnapshot.ComponentStatus.COMPLETE,OptionalDouble.of(70),OptionalDouble.of(70),20,OptionalDouble.of(1400),Set.of()));
        var snapshot=new ProsperitySnapshot(meta,true,ProsperitySnapshot.Status.COMPLETE,components,100,
                OptionalDouble.of(7000),OptionalDouble.of(70),Optional.of(ProsperitySnapshot.Band.ESTABLISHED),List.of());
        var original=new ProsperitySnapshotPayload(51,snapshot);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        ProsperitySnapshotPayload.STREAM_CODEC.encode(buffer,original);var decoded=ProsperitySnapshotPayload.STREAM_CODEC.decode(buffer);
        assertEquals(original,decoded);assertEquals(70,decoded.prosperity().developmentIndex().orElseThrow(),1e-9);
        assertEquals(5,decoded.prosperity().components().size());
    }
}
