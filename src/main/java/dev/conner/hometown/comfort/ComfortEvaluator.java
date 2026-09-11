package dev.conner.hometown.comfort;

import java.util.*;
import net.minecraft.core.BlockPos;
import dev.conner.hometown.observation.ObservationMetadata;
import static dev.conner.hometown.comfort.ComfortSnapshot.*;

/** Pure category and bed-weighted arithmetic, with no world access. */
public final class ComfortEvaluator {
    private ComfortEvaluator(){}
    public static Band band(double score) {
        if(!Double.isFinite(score)||score<0||score>100)throw new IllegalArgumentException("Invalid Comfort score");
        return score<25?Band.BARE:score<50?Band.BASIC:score<75?Band.FURNISHED:Band.WELL_FURNISHED;
    }
    public static Room room(BlockPos key,int beds,int interior,int boundary,Status status,Map<Reason,Integer> reasons,
            Map<ComfortCategory,Integer> hits,int failures,int excluded,ComfortSettings settings) {
        var presence=new EnumMap<ComfortCategory,Presence>(ComfortCategory.class);
        int present=0,duplicates=0;
        for(var c:ComfortCategory.values()) {
            var setting=settings.categories().get(c);int count=hits.getOrDefault(c,0);
            presence.put(c,!setting.enabled()?Presence.DISABLED:count>0?Presence.TRUE:status==Status.COMPLETE?Presence.FALSE:Presence.UNKNOWN);
            if(setting.enabled()&&count>0)present+=setting.weight();
            duplicates+=Math.max(0,count-1);
        }
        int weight=settings.enabledWeight();
        var score=status==Status.COMPLETE&&weight>0?OptionalDouble.of(100.0*present/weight):OptionalDouble.empty();
        return new Room(key,beds,interior,boundary,status,reasons,presence,hits,duplicates,failures,excluded,present,weight,
            score,score.isPresent()?Optional.of(band(score.getAsDouble())):Optional.empty());
    }
    public static ComfortSnapshot town(ObservationMetadata metadata,ComfortSettings settings,boolean housingComplete,
            int expectedBeds,List<Room> rooms,Map<Reason,Integer> reasons,int attempted,int blocks,int blockLimit) {
        var coverage=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
        int assessed=0,beds=0;double weighted=0;int scoredBeds=0;
        for(var room:rooms)if(room.scanStatus()==Status.COMPLETE) {
            assessed++;beds+=room.enclosedBeds();
            for(var c:ComfortCategory.values())if(room.categoryPresence().get(c)==Presence.TRUE)coverage.merge(c,1,Integer::sum);
            if(room.score().isPresent()){weighted+=room.score().getAsDouble()*room.enclosedBeds();scoredBeds+=room.enclosedBeds();}
        }
        boolean complete=housingComplete&&assessed==rooms.size()&&beds==expectedBeds&&reasons.isEmpty();
        var status=!settings.enabled()?Status.DISABLED:complete?Status.COMPLETE:
            rooms.stream().anyMatch(room->room.scanStatus()==Status.COMPLETE||room.scanStatus()==Status.PARTIAL)?Status.PARTIAL:Status.UNAVAILABLE;
        var observed=scoredBeds>0&&settings.enabled()?OptionalDouble.of(weighted/scoredBeds):OptionalDouble.empty();
        var condition=!settings.enabled()?Condition.DISABLED:!complete?Condition.INCOMPLETE:
            settings.enabledWeight()==0?Condition.NO_ENABLED_WEIGHT:expectedBeds==0?Condition.NO_ENCLOSED_BEDS:Condition.OBSERVED;
        var score=condition==Condition.OBSERVED?observed:OptionalDouble.empty();
        return new ComfortSnapshot(metadata,settings.enabled(),status,reasons,condition,
            housingComplete?OptionalInt.of(rooms.size()):OptionalInt.empty(),assessed,
            housingComplete?OptionalInt.of(expectedBeds):OptionalInt.empty(),beds,settings.enabledWeight(),score,observed,
            score.isPresent()?Optional.of(band(score.getAsDouble())):Optional.empty(),coverage,settings.categories(),rooms,
            attempted,settings.maxRooms(),blocks,blockLimit,settings.maxCellsPerRoom());
    }
}
