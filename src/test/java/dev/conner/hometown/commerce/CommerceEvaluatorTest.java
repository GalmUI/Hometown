package dev.conner.hometown.commerce;

import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommerceEvaluatorTest {
    private static final ObservationMetadata META=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",4,120,1,2,3);
    private static CommerceResidentFact fact(boolean baby,String profession){return new CommerceResidentFact(UUID.randomUUID(),baby,ResourceLocation.parse(profession));}
    private static SettlementObservation observation(SettlementStats.Availability availability,List<CommerceResidentFact> facts){
        var stats=new SettlementStats(facts.size(),0,0,0,availability,List.of());
        return new SettlementObservation(stats,facts,facts.size(),0);
    }
    private static List<CommerceResidentFact> employed(int count,String profession){
        var facts=new ArrayList<CommerceResidentFact>();for(int i=0;i<count;i++)facts.add(fact(false,profession));return facts;
    }

    @Test void E01_mixedResidentsKeepPopulationAndUseEligibleAdultDenominator(){
        var facts=new ArrayList<CommerceResidentFact>();
        facts.addAll(employed(3,"minecraft:farmer"));facts.addAll(employed(2,"minecraft:librarian"));facts.add(fact(false,"minecraft:cleric"));facts.add(fact(false,"minecraft:toolsmith"));
        facts.addAll(List.of(fact(false,"minecraft:none"),fact(false,"minecraft:none"),fact(false,"minecraft:none"),fact(true,"minecraft:farmer"),fact(true,"minecraft:none"),fact(false,"minecraft:nitwit")));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(13,result.totalResidents());assertEquals(10,result.eligibleAdults());assertEquals(7,result.employedAdults());assertEquals(3,result.unemployedAdults());
        assertEquals(2,result.excludedBabies());assertEquals(1,result.excludedNitwits());assertEquals(70.0,result.employmentPercent().orElseThrow());
        assertEquals(CommerceSnapshot.EmploymentState.ACTIVE,result.employmentState());assertEquals(4,result.professionDiversity());
        assertEquals(Map.of(ResourceLocation.parse("minecraft:farmer"),3,ResourceLocation.parse("minecraft:librarian"),2,
                ResourceLocation.parse("minecraft:cleric"),1,ResourceLocation.parse("minecraft:toolsmith"),1),result.professionCounts());
    }

    @Test void E02_E05_exactBandBoundariesUseRawRatio(){
        assertBand(0,4,CommerceSnapshot.EmploymentState.UNEMPLOYED,0.0);
        assertBand(1,3,CommerceSnapshot.EmploymentState.LIMITED,25.0);
        assertBand(2,2,CommerceSnapshot.EmploymentState.ACTIVE,50.0);
        assertBand(3,1,CommerceSnapshot.EmploymentState.ACTIVE,75.0);
        assertBand(4,1,CommerceSnapshot.EmploymentState.STRONG,80.0);
        assertBand(9,1,CommerceSnapshot.EmploymentState.STRONG,90.0);
        assertBand(4,0,CommerceSnapshot.EmploymentState.FULLY_EMPLOYED,100.0);
    }
    private static void assertBand(int employed,int unemployed,CommerceSnapshot.EmploymentState state,double percent){
        var facts=new ArrayList<CommerceResidentFact>();facts.addAll(employed(employed,"minecraft:farmer"));for(int i=0;i<unemployed;i++)facts.add(fact(false,"minecraft:none"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(percent,result.employmentPercent().orElseThrow(),1e-9);assertEquals(state,result.employmentState());
    }

    @Test void E06_nitwitBabyAndModdedProfessionRulesAreExact(){
        var facts=List.of(fact(true,"example:engineer"),fact(false,"minecraft:nitwit"),fact(false,"minecraft:none"),fact(false,"example:engineer"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(4,result.totalResidents());assertEquals(2,result.eligibleAdults());assertEquals(1,result.employedAdults());assertEquals(1,result.unemployedAdults());
        assertEquals(1,result.excludedBabies());assertEquals(1,result.excludedNitwits());assertEquals(50.0,result.employmentPercent().orElseThrow());
        assertEquals(Map.of(ResourceLocation.parse("example:engineer"),1),result.professionCounts(),"registered/non-none profession identity is enough; no workstation rule exists");
        assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:none")));assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:nitwit")));
    }

    @Test void E07_zeroEligibleAdultsIsNA_NotZeroPercent(){
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,List.of(
                fact(true,"minecraft:farmer"),fact(true,"minecraft:none"),fact(false,"minecraft:nitwit"))),true);
        assertEquals(3,result.totalResidents());assertEquals(0,result.eligibleAdults());assertTrue(result.employmentPercent().isEmpty());
        assertTrue(result.observedEmploymentPercent().isEmpty());assertEquals(CommerceSnapshot.EmploymentState.NO_ELIGIBLE_ADULTS,result.employmentState());assertEquals(0,result.professionDiversity());
    }

    @Test void E08_partialResidentCoverageShowsObservedRatioWithoutAuthoritativeState(){
        var facts=new ArrayList<CommerceResidentFact>();facts.addAll(employed(6,"minecraft:farmer"));for(int i=0;i<2;i++)facts.add(fact(false,"minecraft:none"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.PARTIAL,facts),true);
        assertEquals(6,result.employedAdults());assertEquals(8,result.eligibleAdults());assertTrue(result.employmentPercent().isEmpty());
        assertEquals(75.0,result.observedEmploymentPercent().orElseThrow());assertEquals(CommerceSnapshot.EmploymentState.INCOMPLETE,result.employmentState());
        assertEquals(CommerceSnapshot.Status.PARTIAL,result.scanStatus());assertTrue(result.reasonCounts().containsKey(CommerceSnapshot.Reason.RESIDENT_DATA_INCOMPLETE));
    }

    @Test void disabledAndUnavailableNeverInventAuthoritativeEmployment(){
        var facts=List.of(fact(false,"minecraft:farmer"));
        var disabled=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),false);
        assertEquals(CommerceSnapshot.Status.DISABLED,disabled.scanStatus());assertEquals(CommerceSnapshot.EmploymentState.DISABLED,disabled.employmentState());assertTrue(disabled.employmentPercent().isEmpty());
        var unavailable=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.UNAVAILABLE,List.of()),true);
        assertEquals(CommerceSnapshot.Status.UNAVAILABLE,unavailable.scanStatus());assertEquals(CommerceSnapshot.EmploymentState.INCOMPLETE,unavailable.employmentState());assertTrue(unavailable.employmentPercent().isEmpty());
    }
}
