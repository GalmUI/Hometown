package dev.conner.hometown.food;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class FoodVarietyEvaluator {
    private FoodVarietyEvaluator() {}

    public static FoodVarietySnapshot evaluate(ObservationMetadata metadata, FoodSnapshot reserves,
            List<FoodStackFact> facts, FoodVarietySettings settings) {
        return evaluate(metadata,reserves,facts,settings,(itemId,group) -> {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) return false;
            return BuiltInRegistries.ITEM.get(itemId).builtInRegistryHolder().is(group.tag);
        });
    }

    /** Injectable matcher keeps arithmetic/priority tests independent of runtime tag binding. */
    public static FoodVarietySnapshot evaluate(ObservationMetadata metadata, FoodSnapshot reserves,
            List<FoodStackFact> facts, FoodVarietySettings settings, BiPredicate<ResourceLocation,FoodGroup> matcher) {
        Objects.requireNonNull(metadata); Objects.requireNonNull(reserves); Objects.requireNonNull(facts); Objects.requireNonNull(settings); Objects.requireNonNull(matcher);
        var totals = new EnumMap<FoodGroup,Long>(FoodGroup.class);
        for (var group : FoodGroup.values()) totals.put(group,0L);
        long unclassified = 0;
        int collisions = 0;
        var unique = new HashSet<ResourceLocation>();
        for (var fact : facts) {
            unique.add(fact.itemId());
            var matches = EnumSet.noneOf(FoodGroup.class);
            for (var group : FoodGroup.values()) if (matcher.test(fact.itemId(),group)) matches.add(group);
            if (matches.size() > 1) collisions++;
            FoodGroup winner = null;
            for (var group : settings.priority()) if (matches.contains(group)) { winner=group; break; }
            if (winner == null) unclassified = Math.addExact(unclassified,fact.nutrition());
            else totals.put(winner,Math.addExact(totals.get(winner),fact.nutrition()));
        }

        var enabledGroups = settings.enabledGroups();
        var qualifications = new EnumMap<FoodGroup,FoodVarietySnapshot.Qualification>(FoodGroup.class);
        OptionalLong required = OptionalLong.empty();
        OptionalDouble observed = OptionalDouble.empty(), authoritative = OptionalDouble.empty();
        FoodVarietySnapshot.VarietyState state;

        if (!settings.enabled() || enabledGroups.isEmpty()) {
            for (var group : FoodGroup.values()) qualifications.put(group,FoodVarietySnapshot.Qualification.DISABLED);
            state = FoodVarietySnapshot.VarietyState.DISABLED;
        } else if (!reserves.diagnostics().populationComplete()) {
            for (var group : FoodGroup.values()) qualifications.put(group,enabledGroups.contains(group)
                    ? FoodVarietySnapshot.Qualification.UNKNOWN : FoodVarietySnapshot.Qualification.DISABLED);
            state = FoodVarietySnapshot.VarietyState.INCOMPLETE;
        } else if (reserves.population() == 0) {
            for (var group : FoodGroup.values()) qualifications.put(group,enabledGroups.contains(group)
                    ? FoodVarietySnapshot.Qualification.UNKNOWN : FoodVarietySnapshot.Qualification.DISABLED);
            state = reserves.scanStatus()==FoodScanStatus.COMPLETE ? FoodVarietySnapshot.VarietyState.NO_RESIDENTS : FoodVarietySnapshot.VarietyState.INCOMPLETE;
        } else {
            long threshold = Math.max(1L,Math.multiplyExact((long)reserves.population(),settings.nutritionPerResidentPerGroup()));
            required = OptionalLong.of(threshold);
            int met = 0;
            for (var group : FoodGroup.values()) {
                if (!enabledGroups.contains(group)) { qualifications.put(group,FoodVarietySnapshot.Qualification.DISABLED); continue; }
                if (totals.get(group) >= threshold) { qualifications.put(group,FoodVarietySnapshot.Qualification.TRUE); met++; }
                else qualifications.put(group,reserves.scanStatus()==FoodScanStatus.COMPLETE
                        ? FoodVarietySnapshot.Qualification.FALSE : FoodVarietySnapshot.Qualification.UNKNOWN);
            }
            if (reserves.scanStatus()!=FoodScanStatus.UNAVAILABLE) observed=OptionalDouble.of(100.0*met/enabledGroups.size());
            if (reserves.scanStatus()==FoodScanStatus.COMPLETE) {
                authoritative=observed;
                double coverage=authoritative.orElseThrow();
                state=coverage==0?FoodVarietySnapshot.VarietyState.NONE:coverage<=40?FoodVarietySnapshot.VarietyState.LIMITED:
                        coverage<100?FoodVarietySnapshot.VarietyState.VARIED:FoodVarietySnapshot.VarietyState.DIVERSE;
            } else state=FoodVarietySnapshot.VarietyState.INCOMPLETE;
        }

        return new FoodVarietySnapshot(metadata,settings.enabled(),reserves.population(),reserves.diagnostics().populationComplete(),
                totals,enabledGroups,required,qualifications,unique.size(),unclassified,observed,authoritative,state,collisions,reserves.scanStatus());
    }
}
