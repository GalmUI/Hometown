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

    @Test void E02_diversityUsesEmployedProfessionIdsOnly(){
        var facts=new ArrayList<CommerceResidentFact>();
        facts.addAll(employed(3,"minecraft:farmer"));facts.addAll(employed(2,"minecraft:librarian"));facts.add(fact(false,"minecraft:cleric"));facts.add(fact(false,"minecraft:toolsmith"));
        facts.add(fact(false,"minecraft:none"));facts.add(fact(false,"minecraft:nitwit"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(4,result.professionDiversity());assertEquals(7,result.employedAdults());
        assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:none")));
        assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:nitwit")));
    }

    @Test void E03_professionChangeKeepsEmploymentAndChangesDiversityOnlyOnIdAppearanceOrDisappearance(){
        var before=new ArrayList<CommerceResidentFact>();before.addAll(employed(2,"minecraft:farmer"));before.add(fact(false,"minecraft:librarian"));before.add(fact(false,"minecraft:none"));
        var farmerToLibrarian=new ArrayList<CommerceResidentFact>();farmerToLibrarian.add(fact(false,"minecraft:farmer"));farmerToLibrarian.addAll(employed(2,"minecraft:librarian"));farmerToLibrarian.add(fact(false,"minecraft:none"));
        var finalFarmerToLibrarian=new ArrayList<CommerceResidentFact>();finalFarmerToLibrarian.addAll(employed(3,"minecraft:librarian"));finalFarmerToLibrarian.add(fact(false,"minecraft:none"));
        var first=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,before),true);
        var changedCounts=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,farmerToLibrarian),true);
        var removedId=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,finalFarmerToLibrarian),true);
        assertEquals(first.employmentPercent().orElseThrow(),changedCounts.employmentPercent().orElseThrow());
        assertEquals(first.employmentPercent().orElseThrow(),removedId.employmentPercent().orElseThrow());
        assertEquals(2,first.professionDiversity());assertEquals(2,changedCounts.professionDiversity(),"changing counts while both IDs remain must not change diversity");
        assertEquals(1,removedId.professionDiversity(),"diversity changes only when the farmer ID disappears");
        assertEquals(1,changedCounts.professionCounts().get(ResourceLocation.parse("minecraft:farmer")));
        assertEquals(2,changedCounts.professionCounts().get(ResourceLocation.parse("minecraft:librarian")));
    }

    @Test void E04_employmentTransitionAndExactRawBandBoundaries(){
        assertBand(7,3,CommerceSnapshot.EmploymentState.ACTIVE,70.0);
        assertBand(8,2,CommerceSnapshot.EmploymentState.STRONG,80.0);
        assertEquals(CommerceSnapshot.EmploymentState.UNEMPLOYED,CommerceEvaluator.stateFor(0));
        assertEquals(CommerceSnapshot.EmploymentState.LIMITED,CommerceEvaluator.stateFor(0.01));
        assertEquals(CommerceSnapshot.EmploymentState.LIMITED,CommerceEvaluator.stateFor(49.99));
        assertEquals(CommerceSnapshot.EmploymentState.ACTIVE,CommerceEvaluator.stateFor(50));
        assertEquals(CommerceSnapshot.EmploymentState.ACTIVE,CommerceEvaluator.stateFor(79.99));
        assertEquals(CommerceSnapshot.EmploymentState.STRONG,CommerceEvaluator.stateFor(80));
        assertEquals(CommerceSnapshot.EmploymentState.STRONG,CommerceEvaluator.stateFor(99.99));
        assertEquals(CommerceSnapshot.EmploymentState.FULLY_EMPLOYED,CommerceEvaluator.stateFor(100));
    }
    private static void assertBand(int employed,int unemployed,CommerceSnapshot.EmploymentState state,double percent){
        var facts=new ArrayList<CommerceResidentFact>();facts.addAll(employed(employed,"minecraft:farmer"));for(int i=0;i<unemployed;i++)facts.add(fact(false,"minecraft:none"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(percent,result.employmentPercent().orElseThrow(),1e-9);assertEquals(state,result.employmentState());
    }

    @Test void E05_babyChangesPopulationOnlyAndBabyNitwitIsClassifiedOnlyAsBaby(){
        var base=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,List.of(
                fact(false,"minecraft:farmer"),fact(false,"minecraft:none"))),true);
        var withBabyNitwit=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,List.of(
                fact(false,"minecraft:farmer"),fact(false,"minecraft:none"),fact(true,"minecraft:nitwit"))),true);
        assertEquals(base.eligibleAdults(),withBabyNitwit.eligibleAdults());assertEquals(base.employmentPercent().orElseThrow(),withBabyNitwit.employmentPercent().orElseThrow());
        assertEquals(base.totalResidents()+1,withBabyNitwit.totalResidents());assertEquals(1,withBabyNitwit.excludedBabies());assertEquals(0,withBabyNitwit.excludedNitwits());
    }

    @Test void E06_nitwitAndModdedProfessionRulesAreExact(){
        var facts=List.of(fact(false,"minecraft:nitwit"),fact(false,"minecraft:none"),fact(false,"example:engineer"));
        var result=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,facts),true);
        assertEquals(3,result.totalResidents());assertEquals(2,result.eligibleAdults());assertEquals(1,result.employedAdults());assertEquals(1,result.unemployedAdults());
        assertEquals(0,result.excludedBabies());assertEquals(1,result.excludedNitwits());assertEquals(50.0,result.employmentPercent().orElseThrow());
        assertEquals(Map.of(ResourceLocation.parse("example:engineer"),1),result.professionCounts(),"registered/non-none profession identity is enough; no workstation rule exists");
        assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:none")));assertFalse(result.professionCounts().containsKey(ResourceLocation.parse("minecraft:nitwit")));
    }

    @Test void E07_zeroEligibleAdultsIsNA_NotZeroPercentAndEmptyEmployedSetCanBeAuthoritativeZero(){
        var noneEligible=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,List.of(
                fact(true,"minecraft:farmer"),fact(true,"minecraft:none"),fact(false,"minecraft:nitwit"))),true);
        assertEquals(3,noneEligible.totalResidents());assertEquals(0,noneEligible.eligibleAdults());assertTrue(noneEligible.employmentPercent().isEmpty());
        assertTrue(noneEligible.observedEmploymentPercent().isEmpty());assertEquals(CommerceSnapshot.EmploymentState.NO_ELIGIBLE_ADULTS,noneEligible.employmentState());assertEquals(0,noneEligible.professionDiversity());
        var unemployed=CommerceEvaluator.evaluate(META,observation(SettlementStats.Availability.COMPLETE,List.of(fact(false,"minecraft:none"),fact(false,"minecraft:none"))),true);
        assertEquals(0.0,unemployed.employmentPercent().orElseThrow());assertEquals(CommerceSnapshot.EmploymentState.UNEMPLOYED,unemployed.employmentState());
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
