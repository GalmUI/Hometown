package dev.conner.hometown.prosperity;

import dev.conner.hometown.commerce.CommerceSnapshot;
import dev.conner.hometown.comfort.ComfortSnapshot;
import dev.conner.hometown.food.*;
import dev.conner.hometown.housing.HousingSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.safety.SafetySnapshot;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProsperityEvaluatorTest {
    private static final UUID ID=UUID.randomUUID();
    private static final ObservationMetadata META=new ObservationMetadata(ID,"minecraft:overworld",7,1200,11,22,33);
    private static final FoodRules FOOD=new FoodRules(20,1,3,7);

    @Test void P01_formulaUsesExactlyFiveRawInputs(){
        var result=evaluate(10,housing(10,10),foodDays(10,3.5),lighting(100),comfort(40),commerce(10,6),settings(20,20,20,20,20));
        assertEquals(ProsperitySnapshot.Status.COMPLETE,result.status());
        assertEquals(7000.0,result.weightedTotal().orElseThrow(),1e-9);
        assertEquals(70.0,result.developmentIndex().orElseThrow(),1e-9);
        assertEquals(ProsperitySnapshot.Band.ESTABLISHED,result.band().orElseThrow());
    }

    @Test void P02_bandsUseRawValueBeforeDisplayRounding(){
        var s=ProsperitySettings.defaults();
        assertEquals(ProsperitySnapshot.Band.STARTING,s.band(24.99));assertEquals(ProsperitySnapshot.Band.DEVELOPING,s.band(25));
        assertEquals(ProsperitySnapshot.Band.DEVELOPING,s.band(49.99));assertEquals(ProsperitySnapshot.Band.ESTABLISHED,s.band(50));
        assertEquals(ProsperitySnapshot.Band.ESTABLISHED,s.band(74.99));assertEquals(ProsperitySnapshot.Band.FLOURISHING,s.band(75));
        var components=List.of(
            component(20,74.6),component(20,74.6),component(20,74.6),component(20,74.6),component(20,74.6));
        var snapshot=new ProsperitySnapshot(META,true,ProsperitySnapshot.Status.COMPLETE,components,100,OptionalDouble.of(7460),OptionalDouble.of(74.6),Optional.of(ProsperitySnapshot.Band.ESTABLISHED),List.of());
        assertEquals(75,snapshot.displayedIndex());assertEquals(ProsperitySnapshot.Band.ESTABLISHED,snapshot.band().orElseThrow());
    }

    @Test void P03_housingUsesUnroundedRatioAndCapsAboveOneHundred(){
        var fractional=evaluate(3,housing(3,2),foodDays(3,7),lighting(100),comfort(100),commerce(3,3),settings(100,0,0,0,0));
        assertEquals(66.66666666666667,fractional.developmentIndex().orElseThrow(),1e-9);
        var capped=evaluate(10,housing(10,14),foodDays(10,7),lighting(100),comfort(100),commerce(10,10),settings(100,0,0,0,0));
        assertEquals(100,capped.developmentIndex().orElseThrow(),1e-9);
    }

    @Test void P04_foodNormalizationUsesProtectedStockedThreshold(){
        assertFood(0,0);assertFood(3.5,50);assertFood(7,100);assertFood(14,100);
        var custom=new FoodRules(20,1,3,10);
        var result=ProsperityEvaluator.evaluate(META,stats(10,SettlementStats.Availability.COMPLETE),housing(10,10),custom.snapshot(10,1,1,1,1000),custom,
                lighting(100),comfort(100),commerce(10,10),settings(0,100,0,0,0));
        assertEquals(50,result.developmentIndex().orElseThrow(),1e-9);
    }
    private static void assertFood(double days,double expected){
        var result=evaluate(10,housing(10,10),foodDays(10,days),lighting(100),comfort(100),commerce(10,10),settings(0,100,0,0,0));
        assertEquals(expected,result.developmentIndex().orElseThrow(),1e-9);
    }

    @Test void P05_P07_missingAndDisabledComfortNeverReweight(){
        var unavailable=ComfortSnapshot.unavailable(META);
        var result=evaluate(10,housing(10,10),foodDays(10,7),lighting(100),unavailable,commerce(10,10),settings(20,20,20,20,20));
        assertEquals(ProsperitySnapshot.Status.INCOMPLETE,result.status());assertTrue(result.developmentIndex().isEmpty());
        assertEquals(ProsperitySnapshot.ComponentStatus.UNAVAILABLE,result.component(ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT).orElseThrow().status());
        var excluded=evaluate(10,housing(10,10),foodDays(10,3.5),lighting(100),unavailable,commerce(10,6),settings(20,20,20,0,20));
        assertEquals(77.5,excluded.developmentIndex().orElseThrow(),1e-9);assertEquals(78,excluded.displayedIndex());assertEquals(ProsperitySnapshot.Band.FLOURISHING,excluded.band().orElseThrow());
        assertEquals(ProsperitySnapshot.ComponentStatus.EXCLUDED,excluded.component(ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT).orElseThrow().status());
        var disabled=disabledComfort();
        var required=evaluate(10,housing(10,10),foodDays(10,7),lighting(100),disabled,commerce(10,10),settings(20,20,20,20,20));
        assertEquals(ProsperitySnapshot.ComponentStatus.MODULE_DISABLED,required.component(ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT).orElseThrow().status());
        assertEquals(ProsperitySnapshot.Status.INCOMPLETE,required.status());
    }

    @Test void P08_populationAuthorityPrecedesComponentScoring(){
        var zero=ProsperityEvaluator.evaluate(META,stats(0,SettlementStats.Availability.COMPLETE),housing(0,0),FOOD.snapshot(0,0,0,0,0),FOOD,
                SafetySnapshot.unavailable(META),ComfortSnapshot.unavailable(META),CommerceSnapshot.unavailable(META),ProsperitySettings.defaults());
        assertEquals(ProsperitySnapshot.Status.NO_RESIDENTS,zero.status());assertTrue(zero.developmentIndex().isEmpty());
        var partial=ProsperityEvaluator.evaluate(META,stats(0,SettlementStats.Availability.PARTIAL),housing(0,0),FoodSnapshot.unavailable(0),FOOD,
                SafetySnapshot.unavailable(META),ComfortSnapshot.unavailable(META),CommerceSnapshot.unavailable(META),ProsperitySettings.defaults());
        assertEquals(ProsperitySnapshot.Status.INCOMPLETE,partial.status());
        var noBeds=evaluate(10,housing(10,0),foodDays(10,7),noBedLighting(),noBedComfort(),commerce(10,10),settings(20,20,20,20,20));
        assertEquals(ProsperitySnapshot.Status.INCOMPLETE,noBeds.status());
    }

    @Test void P09_P12_nonInputsCannotAffectEvaluator(){
        var baseline=evaluate(10,housing(10,10),foodDays(10,7),lighting(80),comfort(60),commerce(10,7),settings(20,20,20,20,20));
        var housingPrivacyChanged=new HousingSnapshot(10,10,10,0,1,0,0,0,0,true,0,1,0,0,10,OptionalInt.of(40));
        var changed=evaluate(10,housingPrivacyChanged,foodDays(10,7),lighting(80),comfort(60),commerceDifferentDiversity(),settings(20,20,20,20,20));
        assertEquals(baseline.developmentIndex().orElseThrow(),changed.developmentIndex().orElseThrow(),1e-9);
    }

    @Test void P15_mismatchedMetadataIsStaleAndBlocksIndex(){
        var other=new ObservationMetadata(ID,"minecraft:overworld",8,1200,11,22,33);
        var stale=new SafetySnapshot(other,true,SafetySnapshot.Status.COMPLETE,Map.of(),Map.of(),Map.of(),Map.of(),0,0,0,4096,1,1,
                SafetySnapshot.Status.COMPLETE,Map.of(),1,1,0,OptionalDouble.of(100),OptionalDouble.of(100),SafetySnapshot.LightingCondition.OBSERVED,1,1,262144,List.of());
        var result=evaluate(10,housing(10,10),foodDays(10,7),stale,comfort(100),commerce(10,10),settings(20,20,20,20,20));
        assertEquals(ProsperitySnapshot.Status.INCOMPLETE,result.status());
        assertEquals(ProsperitySnapshot.ComponentStatus.STALE,result.component(ProsperitySnapshot.ComponentType.RESIDENTIAL_LIGHTING).orElseThrow().status());
    }

    @Test void P16_settingsRejectInvalidWeightsAndBandOrdering(){
        var allZero=new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);for(var t:ProsperitySnapshot.ComponentType.values())allZero.put(t,0);
        assertThrows(IllegalArgumentException.class,()->new ProsperitySettings(false,allZero,25,50,75));
        var valid=new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);for(var t:ProsperitySnapshot.ComponentType.values())valid.put(t,20);
        assertThrows(IllegalArgumentException.class,()->new ProsperitySettings(true,valid,50,25,75));
        valid.put(ProsperitySnapshot.ComponentType.EMPLOYMENT,101);assertThrows(IllegalArgumentException.class,()->new ProsperitySettings(true,valid,25,50,75));
    }

    private static ProsperitySnapshot evaluate(int population,HousingSnapshot housing,FoodSnapshot food,SafetySnapshot safety,ComfortSnapshot comfort,CommerceSnapshot commerce,ProsperitySettings settings){
        return ProsperityEvaluator.evaluate(META,stats(population,SettlementStats.Availability.COMPLETE),housing,food,FOOD,safety,comfort,commerce,settings);
    }
    private static SettlementStats stats(int population,SettlementStats.Availability availability){return new SettlementStats(population,0,0,0,availability,List.of());}
    private static HousingSnapshot housing(int population,int beds){return new HousingSnapshot(population,beds,beds,0,beds,beds,0,0,0,true);}
    private static FoodSnapshot foodDays(int population,double days){long daily=(long)population*20;return FOOD.snapshot(population,1,1,1,Math.round(days*daily));}
    private static SafetySnapshot lighting(double percent){int expected=100,lit=(int)Math.round(percent);return new SafetySnapshot(META,true,SafetySnapshot.Status.COMPLETE,Map.of(),Map.of(),Map.of(),Map.of(),0,0,0,4096,1,1,
            SafetySnapshot.Status.COMPLETE,Map.of(),expected,lit,expected-lit,OptionalDouble.of(percent),OptionalDouble.of(percent),SafetySnapshot.LightingCondition.OBSERVED,1,expected,262144,List.of());}
    private static SafetySnapshot noBedLighting(){return new SafetySnapshot(META,true,SafetySnapshot.Status.COMPLETE,Map.of(),Map.of(),Map.of(),Map.of(),0,0,0,4096,1,1,
            SafetySnapshot.Status.COMPLETE,Map.of(),0,0,0,OptionalDouble.empty(),OptionalDouble.empty(),SafetySnapshot.LightingCondition.NO_ENCLOSED_BEDS,1,0,262144,List.of());}
    private static ComfortSnapshot comfort(double percent){return new ComfortSnapshot(META,true,ComfortSnapshot.Status.COMPLETE,Map.of(),ComfortSnapshot.Condition.OBSERVED,OptionalInt.of(1),1,OptionalInt.of(1),1,75,
            OptionalDouble.of(percent),OptionalDouble.empty(),Optional.of(percent<25?ComfortSnapshot.Band.BARE:percent<50?ComfortSnapshot.Band.BASIC:percent<75?ComfortSnapshot.Band.FURNISHED:ComfortSnapshot.Band.WELL_FURNISHED),Map.of(),Map.of(),List.of(),1,512,0,262144,65536);}
    private static ComfortSnapshot noBedComfort(){return new ComfortSnapshot(META,true,ComfortSnapshot.Status.COMPLETE,Map.of(),ComfortSnapshot.Condition.NO_ENCLOSED_BEDS,OptionalInt.of(0),0,OptionalInt.of(0),0,75,
            OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),Map.of(),Map.of(),List.of(),0,512,0,262144,65536);}
    private static ComfortSnapshot disabledComfort(){return new ComfortSnapshot(META,false,ComfortSnapshot.Status.DISABLED,Map.of(),ComfortSnapshot.Condition.DISABLED,OptionalInt.empty(),0,OptionalInt.empty(),0,75,
            OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),Map.of(),Map.of(),List.of(),0,512,0,262144,65536);}
    private static CommerceSnapshot commerce(int eligible,int employed){int unemployed=eligible-employed;return new CommerceSnapshot(META,true,CommerceSnapshot.Status.COMPLETE,Map.of(),eligible,eligible,employed,unemployed,0,0,
            OptionalDouble.of(100.0*employed/eligible),OptionalDouble.empty(),employed==0?CommerceSnapshot.EmploymentState.UNEMPLOYED:employed==eligible?CommerceSnapshot.EmploymentState.FULLY_EMPLOYED:100.0*employed/eligible<50?CommerceSnapshot.EmploymentState.LIMITED:100.0*employed/eligible<80?CommerceSnapshot.EmploymentState.ACTIVE:CommerceSnapshot.EmploymentState.STRONG,
            employed==0?0:1,employed==0?Map.of():Map.of(ResourceLocation.parse("minecraft:farmer"),employed),eligible,0);}
    private static CommerceSnapshot commerceDifferentDiversity(){return new CommerceSnapshot(META,true,CommerceSnapshot.Status.COMPLETE,Map.of(),10,10,7,3,0,0,OptionalDouble.of(70),OptionalDouble.empty(),CommerceSnapshot.EmploymentState.ACTIVE,2,
            Map.of(ResourceLocation.parse("minecraft:farmer"),4,ResourceLocation.parse("minecraft:librarian"),3),10,0);}
    private static ProsperitySettings settings(int h,int f,int l,int c,int e){var map=new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);map.put(ProsperitySnapshot.ComponentType.HOUSING_SUPPLY,h);map.put(ProsperitySnapshot.ComponentType.FOOD_RESERVES,f);map.put(ProsperitySnapshot.ComponentType.RESIDENTIAL_LIGHTING,l);map.put(ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT,c);map.put(ProsperitySnapshot.ComponentType.EMPLOYMENT,e);return new ProsperitySettings(true,map,25,50,75);}
    private static ProsperitySnapshot.Component component(int weight,double value){return new ProsperitySnapshot.Component(ProsperitySnapshot.ComponentType.values()[componentIndex++%5],ProsperitySnapshot.ComponentStatus.COMPLETE,OptionalDouble.of(value),OptionalDouble.of(value),weight,OptionalDouble.of(value*weight),Set.of());}
    private static int componentIndex;
}
