package dev.conner.hometown.network;

import dev.conner.hometown.history.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistorySnapshotPayloadTest {
    @Test void roundTripPreservesTypedStructuralEvents(){
        UUID town=UUID.randomUUID();
        var event=new HistoryEvent(UUID.randomUUID(),town,7,HistoryEvent.Type.POPULATION_CHANGED,4800,0,91,
                Map.of("previousPopulation",HistoryArgument.intValue(4),"newPopulation",HistoryArgument.intValue(6)));
        var original=new HistorySnapshotPayload(12,town,44,1,3,List.of(event),true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());HistorySnapshotPayload.STREAM_CODEC.encode(buffer,original);
        var decoded=HistorySnapshotPayload.STREAM_CODEC.decode(buffer);
        assertEquals(original,decoded);assertEquals(HistoryArgument.Type.INT,decoded.events().getFirst().arguments().get("newPopulation").type());
    }

    @Test void requestRoundTripAndPageBoundsAreEnforced(){
        UUID town=UUID.randomUUID();var request=new RequestHistoryPagePayload(3,town,9,4);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        RequestHistoryPagePayload.STREAM_CODEC.encode(buffer,request);assertEquals(request,RequestHistoryPagePayload.STREAM_CODEC.decode(buffer));
        assertThrows(IllegalArgumentException.class,()->new RequestHistoryPagePayload(1,town,1,-1));
        var events=new ArrayList<HistoryEvent>();for(int i=0;i<9;i++)events.add(new HistoryEvent(UUID.randomUUID(),town,i+1,
                HistoryEvent.Type.POPULATION_CHANGED,i*20L,0,1,Map.of()));
        assertThrows(IllegalArgumentException.class,()->new HistorySnapshotPayload(1,town,1,0,2,events,true));
    }
}
