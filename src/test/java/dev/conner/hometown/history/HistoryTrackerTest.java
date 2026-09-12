package dev.conner.hometown.history;

import dev.conner.hometown.food.*;
import dev.conner.hometown.housing.HousingSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.prosperity.ProsperitySnapshot;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryTrackerTest {
    private static final FoodRules FOOD=FoodRules.DEFAULT;
    private static final HistorySettings SETTINGS=HistorySettings.defaults();
    private static final HistoryFingerprints FP=new HistoryFingerprints(11,22,33,44);

    @Test void H02_initialEligibleObservationSilentlyBaselinesAllDomains(){
        var f=fixture();f.advance(1,100,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.ESTABLISHED,60,FP);
        var state=f.data.history(f.town.id());
        assertEquals(1,state.events().size(),"only founding exists");
        for(var d:HistoryTownState.Domain.values())assertTrue(state.domain(d).baselineInitialized());
        assertEquals(2,state.highestConfirmedProsperityMilestone());
    }

    @Test void H03_H04_confirmationKeepsOriginalWindowAndReplacementRestartsIt(){
        var f=fixture();f.advance(1,0,10,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);f.data.setDirty(false);
        f.advance(2,100,11,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        assertFalse(f.data.isDirty());assertEquals(1,f.tracker.candidates(f.town.id(),SETTINGS).size());
        f.advance(3,250,11,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        assertEquals(1,f.data.history(f.town.id()).events().size(),"too soon remains candidate");
        f.advance(4,260,12,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(5,459,12,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        assertEquals(1,f.data.history(f.town.id()).events().size());
        f.advance(6,460,12,20,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        var event=last(f,HistoryEvent.Type.POPULATION_CHANGED);assertEquals(10,event.arguments().get("previousPopulation").asInt());assertEquals(12,event.arguments().get("newPopulation").asInt());
    }

    @Test void H05_partialDomainResetsOnlyItsCandidateAndPreservesBaseline(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(2,100,11,9,FoodSnapshot.FoodSecurityState.LOW,ProsperitySnapshot.Band.DEVELOPING,30,FP);
        var partialStats=new SettlementStats(11,0,0,0,SettlementStats.Availability.PARTIAL,List.of());
        f.tracker.advance(f.data,f.town,meta(3,150),partialStats,HousingSnapshot.unavailable(11,0),FoodSnapshot.unavailable(11),prosperity(ProsperitySnapshot.Status.INCOMPLETE,ProsperitySnapshot.Band.STARTING,0),FP,SETTINGS);
        assertTrue(f.tracker.candidates(f.town.id(),SETTINGS).isEmpty());
        assertEquals("10",f.data.history(f.town.id()).domain(HistoryTownState.Domain.POPULATION).baseline());
    }

    @Test void H06_housingShortageStartAndResolveDoNotCoalesce(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(2,100,10,8,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);f.advance(3,300,10,8,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(4,400,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);f.advance(5,600,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        assertNotNull(last(f,HistoryEvent.Type.HOUSING_SHORTAGE_STARTED));assertNotNull(last(f,HistoryEvent.Type.HOUSING_SHORTAGE_RESOLVED));
    }

    @Test void H07_H11_H17_foodAndPopulationUseFixedWindowCoalescingIncludingNetZero(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(2,100,11,10,FoodSnapshot.FoodSecurityState.LOW,ProsperitySnapshot.Band.STARTING,10,FP);f.advance(3,300,11,10,FoodSnapshot.FoodSecurityState.LOW,ProsperitySnapshot.Band.STARTING,10,FP);
        var pop=last(f,HistoryEvent.Type.POPULATION_CHANGED);long sequence=pop.sequenceNumber(),created=pop.createdGameTime();
        f.advance(4,400,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);f.advance(5,600,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        pop=last(f,HistoryEvent.Type.POPULATION_CHANGED);assertEquals(sequence,pop.sequenceNumber());assertEquals(created,pop.createdGameTime());assertEquals(10,pop.arguments().get("newPopulation").asInt());
        var food=last(f,HistoryEvent.Type.FOOD_SECURITY_CHANGED);assertEquals("STABLE",food.arguments().get("previousSecurityState").value());assertEquals("STABLE",food.arguments().get("newSecurityState").value());
    }

    @Test void H08_H09_prosperityUsesHighWaterNoDownwardOrRepeatedMilestones(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        confirmProsperity(f,2,3,100,300,ProsperitySnapshot.Band.FLOURISHING,80);
        assertEquals(1,count(f,HistoryEvent.Type.PROSPERITY_FLOURISHING_REACHED));assertEquals(0,count(f,HistoryEvent.Type.PROSPERITY_DEVELOPING_REACHED));
        confirmProsperity(f,4,5,400,600,ProsperitySnapshot.Band.STARTING,10);
        confirmProsperity(f,6,7,700,900,ProsperitySnapshot.Band.FLOURISHING,90);
        assertEquals(1,count(f,HistoryEvent.Type.PROSPERITY_FLOURISHING_REACHED),"high-water prevents repeat");
    }

    @Test void H10_fingerprintChangeSilentlyRebaselinesAndIncrementsComparisonRevision(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        long rev=f.data.history(f.town.id()).domain(HistoryTownState.Domain.POPULATION).comparisonRevision();
        var changed=new HistoryFingerprints(99,22,33,44);f.advance(2,500,12,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,changed);
        assertEquals(rev+1,f.data.history(f.town.id()).domain(HistoryTownState.Domain.POPULATION).comparisonRevision());assertEquals(1,f.data.history(f.town.id()).events().size());
    }

    @Test void H14_H15_duplicateGenerationIgnoredAndCandidatesDoNotSurviveTrackerRestart(){
        var f=fixture();f.advance(1,0,10,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);f.data.setDirty(false);
        f.advance(2,100,11,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);
        f.advance(2,400,11,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);assertEquals(1,f.data.history(f.town.id()).events().size());
        f.tracker=new HistoryTracker();f.advance(3,400,11,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);assertEquals(1,f.data.history(f.town.id()).events().size());
        f.advance(4,600,11,10,FoodSnapshot.FoodSecurityState.STABLE,ProsperitySnapshot.Band.STARTING,10,FP);assertNotNull(last(f,HistoryEvent.Type.POPULATION_CHANGED));
    }

    @Test void H12_retentionProtectsFoundingAndSequenceNeverReuses(){
        var f=fixture();var state=f.data.history(f.town.id());
        for(int i=0;i<12;i++)state.append(f.town.id(),HistoryEvent.Type.POPULATION_CHANGED,100+i,1,Map.of("previousPopulation",HistoryArgument.intValue(i),"newPopulation",HistoryArgument.intValue(i+1)));
        long next=state.nextSequenceNumber();assertTrue(state.enforceCap(8));assertEquals(8,state.events().size());assertTrue(state.events().stream().anyMatch(e->e.type()==HistoryEvent.Type.TOWN_FOUNDED));
        var appended=state.append(f.town.id(),HistoryEvent.Type.POPULATION_CHANGED,999,1,Map.of());assertEquals(next,appended.sequenceNumber());
    }

    private static void confirmProsperity(Fixture f,long g1,long g2,long t1,long t2,ProsperitySnapshot.Band band,double index){f.advance(g1,t1,10,10,FoodSnapshot.FoodSecurityState.STABLE,band,index,FP);f.advance(g2,t2,10,10,FoodSnapshot.FoodSecurityState.STABLE,band,index,FP);}
    private static long count(Fixture f,HistoryEvent.Type type){return f.data.history(f.town.id()).events().stream().filter(e->e.type()==type).count();}
    private static HistoryEvent last(Fixture f,HistoryEvent.Type type){return f.data.history(f.town.id()).events().stream().filter(e->e.type()==type).max(Comparator.comparingLong(HistoryEvent::sequenceNumber)).orElse(null);}
    private static Fixture fixture(){return new Fixture();}
    private static final class Fixture {
        final Settlement town=new Settlement(UUID.randomUUID(),"Teston",Level.OVERWORLD,BlockPos.ZERO,64,UUID.randomUUID(),"Founder",0);
        final HometownSavedData data=new HometownSavedData();HistoryTracker tracker=new HistoryTracker();
        Fixture(){data.addSettlement(town);}
        void advance(long gen,long time,int pop,int beds,FoodSnapshot.FoodSecurityState foodState,ProsperitySnapshot.Band band,double index,HistoryFingerprints fp){
            tracker.advance(data,town,meta(gen,time),new SettlementStats(pop,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),housing(pop,beds),food(pop,foodState),prosperity(ProsperitySnapshot.Status.COMPLETE,band,index),fp,SETTINGS);
        }
    }
    private static ObservationMetadata meta(long gen,long time){return new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",gen,time,1,1,1);}
    private static HousingSnapshot housing(int pop,int beds){return new HousingSnapshot(pop,beds,beds,0,beds,beds,0,0,0,true);}
    private static FoodSnapshot food(int pop,FoodSnapshot.FoodSecurityState desired){
        long daily=(long)pop*20;long nutrition=switch(desired){case EMPTY,NO_RESIDENTS->0;case CRITICAL->Math.max(1,daily/2);case LOW->daily;case STABLE->daily*3;case STOCKED->daily*7;};
        return FOOD.snapshot(pop,1,1,1,nutrition);
    }
    private static ProsperitySnapshot prosperity(ProsperitySnapshot.Status status,ProsperitySnapshot.Band band,double index){
        var components=new ArrayList<ProsperitySnapshot.Component>();
        for(var type:ProsperitySnapshot.ComponentType.values())components.add(new ProsperitySnapshot.Component(type,ProsperitySnapshot.ComponentStatus.COMPLETE,OptionalDouble.of(index),OptionalDouble.of(index),20,OptionalDouble.of(index*20),Set.of()));
        if(status!=ProsperitySnapshot.Status.COMPLETE)return new ProsperitySnapshot(new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",0,0,1,1,1),true,status,components,100,OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),List.of(ProsperitySnapshot.ComponentType.HOUSING_SUPPLY));
        return new ProsperitySnapshot(new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",0,0,1,1,1),true,status,components,100,OptionalDouble.of(index*100),OptionalDouble.of(index),Optional.of(band),List.of());
    }
}
