package dev.conner.hometown.prosperity;

import dev.conner.hometown.commerce.CommerceSnapshot;
import dev.conner.hometown.comfort.ComfortSnapshot;
import dev.conner.hometown.food.*;
import dev.conner.hometown.housing.HousingSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.safety.SafetySnapshot;
import dev.conner.hometown.settlement.SettlementStats;
import java.util.*;

/** Pure five-input Development Index calculation. This class performs no world access. */
public final class ProsperityEvaluator {
    private ProsperityEvaluator() {}

    public static ProsperitySnapshot evaluate(ObservationMetadata metadata, SettlementStats stats, HousingSnapshot housing,
            FoodSnapshot food, FoodRules foodRules, SafetySnapshot safety, ComfortSnapshot comfort,
            CommerceSnapshot commerce, ProsperitySettings settings) {
        Objects.requireNonNull(metadata);Objects.requireNonNull(stats);Objects.requireNonNull(housing);Objects.requireNonNull(food);
        Objects.requireNonNull(foodRules);Objects.requireNonNull(safety);Objects.requireNonNull(comfort);Objects.requireNonNull(commerce);Objects.requireNonNull(settings);

        var components=new ArrayList<ProsperitySnapshot.Component>(5);
        components.add(housing(metadata,stats,housing,settings));
        components.add(food(metadata,stats,food,foodRules,settings));
        components.add(lighting(metadata,safety,settings));
        components.add(comfort(metadata,comfort,settings));
        components.add(employment(metadata,commerce,settings));
        int totalWeight=settings.totalEnabledWeight();

        if(!settings.enabled()) return new ProsperitySnapshot(metadata,false,ProsperitySnapshot.Status.DISABLED,components,totalWeight,
                OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),List.of());
        if(stats.availability()==SettlementStats.Availability.COMPLETE&&stats.population()==0)
            return new ProsperitySnapshot(metadata,true,ProsperitySnapshot.Status.NO_RESIDENTS,components,totalWeight,
                    OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),List.of());

        var missing=new ArrayList<ProsperitySnapshot.ComponentType>();
        if(stats.availability()!=SettlementStats.Availability.COMPLETE) {
            for(var c:components)if(c.configuredWeight()>0&&!missing.contains(c.type()))missing.add(c.type());
        } else {
            for(var c:components)if(c.configuredWeight()>0&&c.status()!=ProsperitySnapshot.ComponentStatus.COMPLETE)missing.add(c.type());
        }
        if(!missing.isEmpty()) return new ProsperitySnapshot(metadata,true,ProsperitySnapshot.Status.INCOMPLETE,components,totalWeight,
                OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),missing);

        double weighted=0;
        for(var c:components)weighted+=c.weightedContribution().orElseThrow();
        double index=weighted/totalWeight;
        if(!Double.isFinite(index)||index<0||index>100)throw new IllegalArgumentException("Invalid calculated Development Index");
        return new ProsperitySnapshot(metadata,true,ProsperitySnapshot.Status.COMPLETE,components,totalWeight,
                OptionalDouble.of(weighted),OptionalDouble.of(index),Optional.of(settings.band(index)),List.of());
    }

    private static ProsperitySnapshot.Component housing(ObservationMetadata metadata,SettlementStats stats,HousingSnapshot h,ProsperitySettings settings){
        var type=ProsperitySnapshot.ComponentType.HOUSING_SUPPLY;int weight=settings.weight(type);if(weight==0)return excluded(type);
        if(stats.availability()==SettlementStats.Availability.UNAVAILABLE)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.SOURCE_UNAVAILABLE);
        if(stats.availability()!=SettlementStats.Availability.COMPLETE||!h.scanComplete())return missing(type,weight,ProsperitySnapshot.ComponentStatus.PARTIAL,ProsperitySnapshot.Reason.SOURCE_PARTIAL);
        if(stats.population()==0)return missing(type,weight,ProsperitySnapshot.ComponentStatus.NOT_APPLICABLE,ProsperitySnapshot.Reason.NOT_APPLICABLE);
        double raw=100.0*h.enclosedBeds()/stats.population();return complete(type,weight,raw,Math.min(100,raw));
    }
    private static ProsperitySnapshot.Component food(ObservationMetadata metadata,SettlementStats stats,FoodSnapshot f,FoodRules rules,ProsperitySettings settings){
        var type=ProsperitySnapshot.ComponentType.FOOD_RESERVES;int weight=settings.weight(type);if(weight==0)return excluded(type);
        if(f.scanStatus()==FoodScanStatus.UNAVAILABLE)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.SOURCE_UNAVAILABLE);
        if(f.scanStatus()!=FoodScanStatus.COMPLETE)return missing(type,weight,ProsperitySnapshot.ComponentStatus.PARTIAL,ProsperitySnapshot.Reason.SOURCE_PARTIAL);
        if(stats.population()==0||f.state().orElse(null)==FoodSnapshot.FoodSecurityState.NO_RESIDENTS)return missing(type,weight,ProsperitySnapshot.ComponentStatus.NOT_APPLICABLE,ProsperitySnapshot.Reason.NOT_APPLICABLE);
        if(f.reserveDays().isEmpty()||!Double.isFinite(f.reserveDays().getAsDouble())||rules.stockedDays()<=0)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.INVALID_SOURCE_VALUE);
        double raw=f.reserveDays().getAsDouble();return complete(type,weight,raw,Math.min(100,raw/rules.stockedDays()*100));
    }
    private static ProsperitySnapshot.Component lighting(ObservationMetadata metadata,SafetySnapshot s,ProsperitySettings settings){
        var type=ProsperitySnapshot.ComponentType.RESIDENTIAL_LIGHTING;int weight=settings.weight(type);if(weight==0)return excluded(type);
        if(!s.metadata().equals(metadata))return missing(type,weight,ProsperitySnapshot.ComponentStatus.STALE,ProsperitySnapshot.Reason.REVISION_MISMATCH);
        if(!s.enabled()||s.lightingScanStatus()==SafetySnapshot.Status.DISABLED)return missing(type,weight,ProsperitySnapshot.ComponentStatus.MODULE_DISABLED,ProsperitySnapshot.Reason.MODULE_DISABLED);
        if(s.lightingCondition()==SafetySnapshot.LightingCondition.NO_ENCLOSED_BEDS)return missing(type,weight,ProsperitySnapshot.ComponentStatus.NOT_APPLICABLE,ProsperitySnapshot.Reason.NOT_APPLICABLE);
        if(s.lightingScanStatus()==SafetySnapshot.Status.PARTIAL)return missing(type,weight,ProsperitySnapshot.ComponentStatus.PARTIAL,ProsperitySnapshot.Reason.SOURCE_PARTIAL);
        if(s.lightingScanStatus()!=SafetySnapshot.Status.COMPLETE||s.residentialLightingPercent().isEmpty())return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.SOURCE_UNAVAILABLE);
        double raw=s.residentialLightingPercent().getAsDouble();return validPercent(type,weight,raw);
    }
    private static ProsperitySnapshot.Component comfort(ObservationMetadata metadata,ComfortSnapshot c,ProsperitySettings settings){
        var type=ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT;int weight=settings.weight(type);if(weight==0)return excluded(type);
        if(!c.metadata().equals(metadata))return missing(type,weight,ProsperitySnapshot.ComponentStatus.STALE,ProsperitySnapshot.Reason.REVISION_MISMATCH);
        if(!c.enabled()||c.scanStatus()==ComfortSnapshot.Status.DISABLED||c.condition()==ComfortSnapshot.Condition.DISABLED)return missing(type,weight,ProsperitySnapshot.ComponentStatus.MODULE_DISABLED,ProsperitySnapshot.Reason.MODULE_DISABLED);
        if(c.condition()==ComfortSnapshot.Condition.NO_ENCLOSED_BEDS||c.condition()==ComfortSnapshot.Condition.NO_ENABLED_WEIGHT)return missing(type,weight,ProsperitySnapshot.ComponentStatus.NOT_APPLICABLE,ProsperitySnapshot.Reason.NOT_APPLICABLE);
        if(c.scanStatus()==ComfortSnapshot.Status.PARTIAL)return missing(type,weight,ProsperitySnapshot.ComponentStatus.PARTIAL,ProsperitySnapshot.Reason.SOURCE_PARTIAL);
        if(c.scanStatus()!=ComfortSnapshot.Status.COMPLETE||c.residentialComfortPercent().isEmpty())return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.SOURCE_UNAVAILABLE);
        return validPercent(type,weight,c.residentialComfortPercent().getAsDouble());
    }
    private static ProsperitySnapshot.Component employment(ObservationMetadata metadata,CommerceSnapshot c,ProsperitySettings settings){
        var type=ProsperitySnapshot.ComponentType.EMPLOYMENT;int weight=settings.weight(type);if(weight==0)return excluded(type);
        if(!c.metadata().equals(metadata))return missing(type,weight,ProsperitySnapshot.ComponentStatus.STALE,ProsperitySnapshot.Reason.REVISION_MISMATCH);
        if(!c.enabled()||c.scanStatus()==CommerceSnapshot.Status.DISABLED)return missing(type,weight,ProsperitySnapshot.ComponentStatus.MODULE_DISABLED,ProsperitySnapshot.Reason.MODULE_DISABLED);
        if(c.scanStatus()==CommerceSnapshot.Status.PARTIAL)return missing(type,weight,ProsperitySnapshot.ComponentStatus.PARTIAL,ProsperitySnapshot.Reason.SOURCE_PARTIAL);
        if(c.scanStatus()!=CommerceSnapshot.Status.COMPLETE)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.SOURCE_UNAVAILABLE);
        if(c.employmentState()==CommerceSnapshot.EmploymentState.NO_ELIGIBLE_ADULTS)return missing(type,weight,ProsperitySnapshot.ComponentStatus.NOT_APPLICABLE,ProsperitySnapshot.Reason.NOT_APPLICABLE);
        if(c.employmentPercent().isEmpty())return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.INVALID_SOURCE_VALUE);
        return validPercent(type,weight,c.employmentPercent().getAsDouble());
    }
    private static ProsperitySnapshot.Component validPercent(ProsperitySnapshot.ComponentType type,int weight,double raw){
        if(!Double.isFinite(raw)||raw<0||raw>100)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.INVALID_SOURCE_VALUE);
        return complete(type,weight,raw,raw);
    }
    private static ProsperitySnapshot.Component complete(ProsperitySnapshot.ComponentType type,int weight,double raw,double normalized){
        if(!Double.isFinite(raw)||!Double.isFinite(normalized)||normalized<0||normalized>100)return missing(type,weight,ProsperitySnapshot.ComponentStatus.UNAVAILABLE,ProsperitySnapshot.Reason.INVALID_SOURCE_VALUE);
        return new ProsperitySnapshot.Component(type,ProsperitySnapshot.ComponentStatus.COMPLETE,OptionalDouble.of(raw),OptionalDouble.of(normalized),weight,OptionalDouble.of(normalized*weight),Set.of());
    }
    private static ProsperitySnapshot.Component excluded(ProsperitySnapshot.ComponentType type){return new ProsperitySnapshot.Component(type,ProsperitySnapshot.ComponentStatus.EXCLUDED,OptionalDouble.empty(),OptionalDouble.empty(),0,OptionalDouble.of(0),Set.of());}
    private static ProsperitySnapshot.Component missing(ProsperitySnapshot.ComponentType type,int weight,ProsperitySnapshot.ComponentStatus status,ProsperitySnapshot.Reason reason){return new ProsperitySnapshot.Component(type,status,OptionalDouble.empty(),OptionalDouble.empty(),weight,OptionalDouble.empty(),Set.of(reason));}
}
