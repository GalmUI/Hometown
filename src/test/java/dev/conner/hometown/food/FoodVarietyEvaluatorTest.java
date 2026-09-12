package dev.conner.hometown.food;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodVarietyEvaluatorTest {
    private static final ObservationMetadata META=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",1,20,3,4,5);
    private static ResourceLocation id(String value){return ResourceLocation.fromNamespaceAndPath("test",value);}
    private static FoodStackFact fact(String value,long nutrition){return new FoodStackFact(id(value),nutrition);}
    private static FoodVarietySettings settings(Set<FoodGroup> enabled){
        var map=new EnumMap<FoodGroup,Boolean>(FoodGroup.class);for(var group:FoodGroup.values())map.put(group,enabled.contains(group));
        return new FoodVarietySettings(true,map,2,FoodGroup.DEFAULT_PRIORITY);
    }
    private static FoodVarietySettings defaults(){return settings(EnumSet.allOf(FoodGroup.class));}
    private static java.util.function.BiPredicate<ResourceLocation,FoodGroup> matcher(Map<ResourceLocation,Set<FoodGroup>> memberships){
        return (item,group)->memberships.getOrDefault(item,Set.of()).contains(group);
    }
    private static Map<ResourceLocation,Set<FoodGroup>> oneToOne(String... names){
        var map=new HashMap<ResourceLocation,Set<FoodGroup>>();var groups=FoodGroup.values();for(int i=0;i<names.length;i++)map.put(id(names[i]),Set.of(groups[i]));return map;
    }

    @Test void F01_thresholdEqualityCoverageAndBands(){
        var membership=oneToOne("grain","veg","fruit","protein","meal");
        var reserves=FoodRules.DEFAULT.snapshot(10,1,4,4,139);
        var first=FoodVarietyEvaluator.evaluate(META,reserves,List.of(fact("grain",40),fact("veg",20),fact("fruit",19),fact("meal",60)),defaults(),matcher(membership));
        assertEquals(20,first.requiredNutritionPerGroup().orElseThrow());
        assertEquals(3,first.qualifyingEnabledGroups());assertEquals(60.0,first.authoritativeCoverage().orElseThrow());
        assertEquals(FoodVarietySnapshot.VarietyState.VARIED,first.varietyState());
        assertEquals(FoodVarietySnapshot.Qualification.TRUE,first.qualifications().get(FoodGroup.VEGETABLES),"threshold equality qualifies");
        var four=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,4,4,140),List.of(fact("grain",40),fact("veg",20),fact("fruit",20),fact("meal",60)),defaults(),matcher(membership));
        assertEquals(80.0,four.authoritativeCoverage().orElseThrow());assertEquals(FoodVarietySnapshot.VarietyState.VARIED,four.varietyState());
        var all=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,5,5,160),List.of(fact("grain",40),fact("veg",20),fact("fruit",20),fact("protein",20),fact("meal",60)),defaults(),matcher(membership));
        assertEquals(100.0,all.authoritativeCoverage().orElseThrow());assertEquals(FoodVarietySnapshot.VarietyState.DIVERSE,all.varietyState());
        for(int met=0;met<=2;met++){
            var facts=new ArrayList<FoodStackFact>();for(int i=0;i<met;i++)facts.add(fact(new String[]{"grain","veg"}[i],20));
            var result=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,met==0?0:1,met,met,20L*met),facts,defaults(),matcher(membership));
            assertEquals(met==0?FoodVarietySnapshot.VarietyState.NONE:FoodVarietySnapshot.VarietyState.LIMITED,result.varietyState());
            assertEquals(20.0*met,result.authoritativeCoverage().orElseThrow());
        }
    }

    @Test void F02_priorityCollisionAndDisabledWinnerNeverReroutes(){
        var overlap=id("overlap");var memberships=Map.of(overlap,Set.of(FoodGroup.PREPARED_MEALS,FoodGroup.PROTEIN,FoodGroup.VEGETABLES));
        var result=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,1,1,30),List.of(new FoodStackFact(overlap,30)),defaults(),matcher(memberships));
        assertEquals(30,result.perGroupNutrition().get(FoodGroup.PREPARED_MEALS));
        assertEquals(0,result.perGroupNutrition().get(FoodGroup.PROTEIN));assertEquals(1,result.collisionCount());
        var enabled=EnumSet.allOf(FoodGroup.class);enabled.remove(FoodGroup.PREPARED_MEALS);
        var disabledWinner=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,1,1,30),List.of(new FoodStackFact(overlap,30)),settings(enabled),matcher(memberships));
        assertEquals(30,disabledWinner.perGroupNutrition().get(FoodGroup.PREPARED_MEALS));
        assertEquals(FoodVarietySnapshot.Qualification.DISABLED,disabledWinner.qualifications().get(FoodGroup.PREPARED_MEALS));
        assertEquals(0,disabledWinner.perGroupNutrition().get(FoodGroup.PROTEIN),"disabled winner must not reroute to lower priority group");
    }

    @Test void F03_unclassifiedAndUniqueUseRegistryIdentity(){
        var apple=id("apple"),odd=id("odd_food");var memberships=Map.of(apple,Set.of(FoodGroup.FRUIT));
        var result=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(5,1,3,2,22),List.of(
                new FoodStackFact(apple,6),new FoodStackFact(apple,6),new FoodStackFact(odd,10)),defaults(),matcher(memberships));
        assertEquals(2,result.uniqueFoodCount(),"component/stack differences must not create new registry identities");
        assertEquals(12,result.perGroupNutrition().get(FoodGroup.FRUIT));assertEquals(10,result.unclassifiedNutrition());
    }

    @Test void F04_zeroPopulationNoEnabledGroupsAndReducedDenominator(){
        var membership=oneToOne("grain","veg","fruit","protein","meal");
        var zero=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(0,1,1,1,10),List.of(fact("grain",10)),defaults(),matcher(membership));
        assertEquals(FoodVarietySnapshot.VarietyState.NO_RESIDENTS,zero.varietyState());assertTrue(zero.authoritativeCoverage().isEmpty());assertTrue(zero.requiredNutritionPerGroup().isEmpty());
        var noneEnabled=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,1,1,20),List.of(fact("grain",20)),settings(Set.of()),matcher(membership));
        assertEquals(FoodVarietySnapshot.VarietyState.DISABLED,noneEnabled.varietyState());assertTrue(noneEnabled.authoritativeCoverage().isEmpty());
        var reduced=FoodVarietyEvaluator.evaluate(META,FoodRules.DEFAULT.snapshot(10,1,1,1,20),List.of(fact("grain",20)),settings(EnumSet.of(FoodGroup.GRAINS,FoodGroup.VEGETABLES)),matcher(membership));
        assertEquals(50.0,reduced.authoritativeCoverage().orElseThrow());assertEquals(FoodVarietySnapshot.VarietyState.VARIED,reduced.varietyState());
    }

    @Test void F05_partialStorageKeepsKnownTrueButUnknownAbsenceAndIncompletePopulationIsNA(){
        var membership=oneToOne("grain","veg","fruit","protein","meal");
        var partialDiag=FoodScanDiagnostics.empty(Set.of(FoodScanReason.UNLOADED_CHUNKS),true);
        var partial=FoodRules.DEFAULT.snapshot(10,1,1,1,20,FoodScanStatus.PARTIAL,partialDiag);
        var observed=FoodVarietyEvaluator.evaluate(META,partial,List.of(fact("grain",20)),defaults(),matcher(membership));
        assertEquals(FoodVarietySnapshot.Qualification.TRUE,observed.qualifications().get(FoodGroup.GRAINS));
        assertEquals(FoodVarietySnapshot.Qualification.UNKNOWN,observed.qualifications().get(FoodGroup.VEGETABLES));
        assertEquals(20.0,observed.observedCoverage().orElseThrow());assertTrue(observed.authoritativeCoverage().isEmpty());assertEquals(FoodVarietySnapshot.VarietyState.INCOMPLETE,observed.varietyState());
        var incompleteDiag=FoodScanDiagnostics.empty(Set.of(FoodScanReason.POPULATION_INCOMPLETE),false);
        var incomplete=FoodRules.DEFAULT.snapshot(10,1,1,1,20,FoodScanStatus.PARTIAL,incompleteDiag);
        var unknown=FoodVarietyEvaluator.evaluate(META,incomplete,List.of(fact("grain",20)),defaults(),matcher(membership));
        assertTrue(unknown.requiredNutritionPerGroup().isEmpty());assertTrue(unknown.observedCoverage().isEmpty());
        assertTrue(unknown.qualifications().values().stream().filter(q->q!=FoodVarietySnapshot.Qualification.DISABLED).allMatch(q->q==FoodVarietySnapshot.Qualification.UNKNOWN));
    }
}
