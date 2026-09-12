package dev.conner.hometown.history;

import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryPersistenceTest {
    @Test void H19_v1MigrationPreservesTownAndConvertsOnlyAuthoritativeFoundingRecord(){
        var town=new Settlement(UUID.randomUUID(),"Oldtown",Level.OVERWORLD,new BlockPos(12,70,-4),64,UUID.randomUUID(),"Alex",48000);
        var v1=new CompoundTag();v1.putInt("DataVersion",1);var settlements=new ListTag();settlements.add(town.toTag());v1.put("Settlements",settlements);
        var migrated=HometownSavedData.load(v1,null);
        assertEquals(town,migrated.getSettlement(town.id()).orElseThrow());assertTrue(migrated.isDirty());
        var history=migrated.getHistory(town.id()).orElseThrow();assertEquals(1,history.events().size());
        var founding=history.events().getFirst();assertEquals(HistoryEvent.Type.TOWN_FOUNDED,founding.type());assertEquals(2,founding.observedDay());
        assertEquals("Oldtown",founding.arguments().get("townName").value());assertEquals("Alex",founding.arguments().get("founderName").value());
        for(var domain:HistoryTownState.Domain.values())assertFalse(history.domain(domain).baselineInitialized(),"migration must not invent derived baselines");

        var v2=migrated.save(new CompoundTag(),null);assertEquals(2,v2.getInt("DataVersion"));
        var reloaded=HometownSavedData.load(v2,null);assertFalse(reloaded.isDirty(),"unchanged v2 restart must not look like a migration");
        assertEquals(town,reloaded.getSettlement(town.id()).orElseThrow());assertEquals(founding,reloaded.getHistory(town.id()).orElseThrow().events().getFirst());
    }

    @Test void H12_eventStateRoundTripsTypedArgumentsRevisionsAndNextSequence(){
        var data=new HometownSavedData();var town=new Settlement(UUID.randomUUID(),"Typed",Level.NETHER,BlockPos.ZERO,80,UUID.randomUUID(),"Conner",24000);data.addSettlement(town);
        var state=data.history(town.id());state.domain(HistoryTownState.Domain.POPULATION).setFingerprint(123);state.domain(HistoryTownState.Domain.POPULATION).setBaseline("7");
        var args=new LinkedHashMap<String,HistoryArgument>();args.put("i",HistoryArgument.intValue(7));args.put("d",HistoryArgument.doubleValue(12.5));args.put("pos",HistoryArgument.blockPos(new BlockPos(1,2,3)));args.put("id",HistoryArgument.uuid(town.id()));
        var event=state.append(town.id(),HistoryEvent.Type.POPULATION_CHANGED,25000,state.domain(HistoryTownState.Domain.POPULATION).comparisonRevision(),args);long expectedNext=state.nextSequenceNumber();
        var loaded=HometownSavedData.load(data.save(new CompoundTag(),null),null);var copy=loaded.history(town.id());
        assertEquals(expectedNext,copy.nextSequenceNumber());assertEquals("7",copy.domain(HistoryTownState.Domain.POPULATION).baseline());assertEquals(123,copy.domain(HistoryTownState.Domain.POPULATION).fingerprint());
        var eventCopy=copy.events().stream().filter(e->e.sequenceNumber()==event.sequenceNumber()).findFirst().orElseThrow();assertEquals(7,eventCopy.arguments().get("i").asInt());assertEquals(12.5,eventCopy.arguments().get("d").asDouble());assertEquals(new BlockPos(1,2,3),eventCopy.arguments().get("pos").asBlockPos());
    }

    @Test void malformedOrForeignHistoryOwnerFailsInsteadOfAttachingToWrongTown(){
        var data=new HometownSavedData();var town=new Settlement(UUID.randomUUID(),"Town",Level.OVERWORLD,BlockPos.ZERO,64,UUID.randomUUID(),"F",0);data.addSettlement(town);
        var tag=data.save(new CompoundTag(),null);var history=tag.getList("History",Tag.TAG_COMPOUND);history.getCompound(0).putUUID("SettlementId",UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->HometownSavedData.load(tag,null));
    }
}
