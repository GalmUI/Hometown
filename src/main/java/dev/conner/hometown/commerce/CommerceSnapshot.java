package dev.conner.hometown.commerce;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative Commerce observation derived only from copied resident facts. */
public record CommerceSnapshot(ObservationMetadata metadata, boolean enabled, Status scanStatus,
        Map<Reason,Integer> reasonCounts, int totalResidents, int eligibleAdults, int employedAdults,
        int unemployedAdults, int excludedBabies, int excludedNitwits, OptionalDouble employmentPercent,
        OptionalDouble observedEmploymentPercent, EmploymentState employmentState, int professionDiversity,
        Map<ResourceLocation,Integer> professionCounts, int residentInspections, int duplicateResidents) {
    public enum Status { COMPLETE, PARTIAL, UNAVAILABLE, DISABLED }
    public enum Reason { RESIDENT_DATA_INCOMPLETE, NO_SETTLEMENT_DATA, INTERNAL_ERROR }
    public enum EmploymentState { UNEMPLOYED, LIMITED, ACTIVE, STRONG, FULLY_EMPLOYED, NO_ELIGIBLE_ADULTS, INCOMPLETE, DISABLED }

    public CommerceSnapshot {
        Objects.requireNonNull(metadata);Objects.requireNonNull(scanStatus);Objects.requireNonNull(reasonCounts);
        Objects.requireNonNull(employmentPercent);Objects.requireNonNull(observedEmploymentPercent);Objects.requireNonNull(employmentState);
        var reasons=new EnumMap<Reason,Integer>(Reason.class);reasonCounts.forEach((reason,count)->{if(count==null||count<=0)throw new IllegalArgumentException("Invalid Commerce reason count");reasons.put(reason,count);});reasonCounts=Map.copyOf(reasons);
        var professions=new TreeMap<ResourceLocation,Integer>();professionCounts.forEach((id,count)->{Objects.requireNonNull(id);if(count==null||count<=0)throw new IllegalArgumentException("Invalid profession count");professions.put(id,count);});professionCounts=Map.copyOf(professions);
        if(totalResidents<0||eligibleAdults<0||employedAdults<0||unemployedAdults<0||excludedBabies<0||excludedNitwits<0
                ||professionDiversity<0||residentInspections<0||duplicateResidents<0||duplicateResidents>residentInspections
                ||eligibleAdults!=employedAdults+unemployedAdults||totalResidents!=eligibleAdults+excludedBabies+excludedNitwits
                ||professionDiversity!=professionCounts.size()||professionCounts.values().stream().mapToInt(Integer::intValue).sum()!=employedAdults
                ||invalidPercent(employmentPercent)||invalidPercent(observedEmploymentPercent))
            throw new IllegalArgumentException("Invalid Commerce snapshot");
        boolean authoritative=employmentPercent.isPresent();
        if(authoritative!=(enabled&&scanStatus==Status.COMPLETE&&eligibleAdults>0))throw new IllegalArgumentException("Commerce authority mismatch");
        if(observedEmploymentPercent.isPresent()&&(!enabled||scanStatus!=Status.PARTIAL||eligibleAdults==0))throw new IllegalArgumentException("Invalid observed Commerce ratio");
        if(scanStatus==Status.DISABLED!=(employmentState==EmploymentState.DISABLED))throw new IllegalArgumentException("Commerce disabled state mismatch");
        if(scanStatus==Status.COMPLETE&&eligibleAdults==0&&employmentState!=EmploymentState.NO_ELIGIBLE_ADULTS)throw new IllegalArgumentException("Commerce zero eligible state mismatch");
        if(scanStatus!=Status.COMPLETE&&scanStatus!=Status.DISABLED&&employmentState!=EmploymentState.INCOMPLETE)throw new IllegalArgumentException("Commerce incomplete state mismatch");
        if(authoritative&&switch(employmentState){case UNEMPLOYED,LIMITED,ACTIVE,STRONG,FULLY_EMPLOYED->false;default->true;})throw new IllegalArgumentException("Commerce band mismatch");
    }
    private static boolean invalidPercent(OptionalDouble value){return value.isPresent()&&(!Double.isFinite(value.getAsDouble())||value.getAsDouble()<0||value.getAsDouble()>100);}

    public static CommerceSnapshot unavailable(ObservationMetadata metadata){
        return new CommerceSnapshot(metadata,true,Status.UNAVAILABLE,Map.of(Reason.NO_SETTLEMENT_DATA,1),0,0,0,0,0,0,
                OptionalDouble.empty(),OptionalDouble.empty(),EmploymentState.INCOMPLETE,0,Map.of(),0,0);
    }

    public void write(FriendlyByteBuf b){
        metadata.write(b);b.writeBoolean(enabled);b.writeEnum(scanStatus);b.writeVarInt(reasonCounts.size());
        new TreeMap<>(reasonCounts).forEach((reason,count)->{b.writeEnum(reason);b.writeVarInt(count);});
        for(int value:new int[]{totalResidents,eligibleAdults,employedAdults,unemployedAdults,excludedBabies,excludedNitwits})b.writeVarInt(value);
        writePercent(b,employmentPercent);writePercent(b,observedEmploymentPercent);b.writeEnum(employmentState);b.writeVarInt(professionDiversity);
        b.writeVarInt(professionCounts.size());professionCounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{b.writeResourceLocation(entry.getKey());b.writeVarInt(entry.getValue());});
        b.writeVarInt(residentInspections);b.writeVarInt(duplicateResidents);
    }
    public static CommerceSnapshot read(FriendlyByteBuf b){
        var metadata=ObservationMetadata.read(b);boolean enabled=b.readBoolean();var status=b.readEnum(Status.class);int reasonSize=b.readVarInt();
        if(reasonSize<0||reasonSize>Reason.values().length)throw new IllegalArgumentException("Invalid Commerce reason count");
        var reasons=new EnumMap<Reason,Integer>(Reason.class);for(int i=0;i<reasonSize;i++){var reason=b.readEnum(Reason.class);int count=b.readVarInt();if(count<=0||reasons.put(reason,count)!=null)throw new IllegalArgumentException("Invalid Commerce reason");}
        int total=b.readVarInt(),eligible=b.readVarInt(),employed=b.readVarInt(),unemployed=b.readVarInt(),babies=b.readVarInt(),nitwits=b.readVarInt();
        var authority=readPercent(b);
        var observed=readPercent(b);
        var state=b.readEnum(EmploymentState.class);int diversity=b.readVarInt();int professionSize=b.readVarInt();
        if(professionSize<0||professionSize>65536)throw new IllegalArgumentException("Invalid Commerce profession count");
        var professions=new TreeMap<ResourceLocation,Integer>();for(int i=0;i<professionSize;i++){var id=b.readResourceLocation();int count=b.readVarInt();if(count<=0||professions.put(id,count)!=null)throw new IllegalArgumentException("Invalid Commerce profession record");}
        return new CommerceSnapshot(metadata,enabled,status,reasons,total,eligible,employed,unemployed,babies,nitwits,authority,observed,state,diversity,professions,b.readVarInt(),b.readVarInt());
    }
    private static void writePercent(FriendlyByteBuf b,OptionalDouble value){b.writeBoolean(value.isPresent());if(value.isPresent())b.writeDouble(value.getAsDouble());}
    private static OptionalDouble readPercent(FriendlyByteBuf b){if(!b.readBoolean())return OptionalDouble.empty();double value=b.readDouble();if(!Double.isFinite(value)||value<0||value>100)throw new IllegalArgumentException("Invalid Commerce percent");return OptionalDouble.of(value);}
}
