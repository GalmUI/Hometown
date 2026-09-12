package dev.conner.hometown.commerce;

import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.settlement.SettlementObservation;
import dev.conner.hometown.settlement.SettlementStats;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Pure Commerce calculation over copied resident facts; performs no world access. */
public final class CommerceEvaluator {
    private static final ResourceLocation NONE=ResourceLocation.fromNamespaceAndPath("minecraft","none");
    private static final ResourceLocation NITWIT=ResourceLocation.fromNamespaceAndPath("minecraft","nitwit");
    private CommerceEvaluator() {}

    public static CommerceSnapshot evaluate(ObservationMetadata metadata, SettlementObservation observation, boolean enabled) {
        Objects.requireNonNull(metadata);Objects.requireNonNull(observation);
        var counts=new TreeMap<ResourceLocation,Integer>();
        int babies=0,nitwits=0,employed=0,unemployed=0;
        for(var fact:observation.residentFacts()) {
            if(fact.baby()){babies++;continue;}
            if(fact.professionId().equals(NITWIT)){nitwits++;continue;}
            if(fact.professionId().equals(NONE)){unemployed++;continue;}
            employed++;counts.merge(fact.professionId(),1,Integer::sum);
        }
        int total=observation.residentFacts().size(),eligible=employed+unemployed;
        if(!enabled)return new CommerceSnapshot(metadata,false,CommerceSnapshot.Status.DISABLED,Map.of(),total,eligible,employed,unemployed,babies,nitwits,
                OptionalDouble.empty(),OptionalDouble.empty(),CommerceSnapshot.EmploymentState.DISABLED,counts.size(),counts,observation.residentInspections(),observation.duplicateResidents());
        var availability=observation.stats().availability();
        if(availability==SettlementStats.Availability.UNAVAILABLE)return new CommerceSnapshot(metadata,true,CommerceSnapshot.Status.UNAVAILABLE,
                Map.of(CommerceSnapshot.Reason.NO_SETTLEMENT_DATA,1),total,eligible,employed,unemployed,babies,nitwits,OptionalDouble.empty(),OptionalDouble.empty(),
                CommerceSnapshot.EmploymentState.INCOMPLETE,counts.size(),counts,observation.residentInspections(),observation.duplicateResidents());
        double percent=eligible==0?Double.NaN:100.0*employed/eligible;
        if(availability==SettlementStats.Availability.PARTIAL)return new CommerceSnapshot(metadata,true,CommerceSnapshot.Status.PARTIAL,
                Map.of(CommerceSnapshot.Reason.RESIDENT_DATA_INCOMPLETE,1),total,eligible,employed,unemployed,babies,nitwits,OptionalDouble.empty(),
                eligible>0?OptionalDouble.of(percent):OptionalDouble.empty(),CommerceSnapshot.EmploymentState.INCOMPLETE,counts.size(),counts,
                observation.residentInspections(),observation.duplicateResidents());
        if(eligible==0)return new CommerceSnapshot(metadata,true,CommerceSnapshot.Status.COMPLETE,Map.of(),total,0,0,0,babies,nitwits,
                OptionalDouble.empty(),OptionalDouble.empty(),CommerceSnapshot.EmploymentState.NO_ELIGIBLE_ADULTS,counts.size(),counts,
                observation.residentInspections(),observation.duplicateResidents());
        var state=stateFor(percent);
        return new CommerceSnapshot(metadata,true,CommerceSnapshot.Status.COMPLETE,Map.of(),total,eligible,employed,unemployed,babies,nitwits,
                OptionalDouble.of(percent),OptionalDouble.empty(),state,counts.size(),counts,observation.residentInspections(),observation.duplicateResidents());
    }

    static CommerceSnapshot.EmploymentState stateFor(double percent) {
        if(!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Invalid employment percentage");
        return percent==0?CommerceSnapshot.EmploymentState.UNEMPLOYED:percent<50?CommerceSnapshot.EmploymentState.LIMITED:
                percent<80?CommerceSnapshot.EmploymentState.ACTIVE:percent<100?CommerceSnapshot.EmploymentState.STRONG:CommerceSnapshot.EmploymentState.FULLY_EMPLOYED;
    }
}
