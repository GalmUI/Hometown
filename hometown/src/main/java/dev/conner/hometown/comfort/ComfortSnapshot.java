package dev.conner.hometown.comfort;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.conner.hometown.observation.ObservationMetadata;

/** Copied scores and diagnostics only; room cell geometry remains server-local. */
public record ComfortSnapshot(ObservationMetadata metadata,boolean enabled,Status scanStatus,Map<Reason,Integer> reasonCounts,
        Condition condition,OptionalInt expectedRooms,int assessedRooms,OptionalInt expectedEnclosedBeds,int assessedEnclosedBeds,
        int enabledWeight,OptionalDouble residentialComfortPercent,OptionalDouble observedRoomComfortPercent,Optional<Band> band,
        Map<ComfortCategory,Integer> categoryCoverage,Map<ComfortCategory,ComfortSettings.Category> categories,
        List<Room> roomRecords,int roomsAttempted,int roomLimit,int blockInspections,int sharedBlockLimit,int cellsPerRoomLimit) {
    public enum Status { COMPLETE, PARTIAL, UNAVAILABLE, DISABLED }
    public enum Reason { UNLOADED_CHUNKS, ROOM_DATA_INCOMPLETE, SCAN_LIMIT_REACHED, SAMPLE_OUT_OF_BOUNDS, INTERNAL_ERROR }
    public enum Condition { OBSERVED, INCOMPLETE, NO_ENCLOSED_BEDS, NO_ENABLED_WEIGHT, DISABLED }
    public enum Presence { TRUE, FALSE, UNKNOWN, DISABLED }
    public enum Band { BARE, BASIC, FURNISHED, WELL_FURNISHED }
    public record Room(BlockPos roomKey,int enclosedBeds,int interiorCellsConsidered,int boundaryCellsConsidered,
            Status scanStatus,Map<Reason,Integer> reasonCounts,Map<ComfortCategory,Presence> categoryPresence,
            Map<ComfortCategory,Integer> perCategoryQualifyingHitCount,int ignoredDuplicateHits,int statePredicateFailures,
            int excludedBlocks,int presentWeight,int enabledWeight,OptionalDouble score,Optional<Band> band) {
        public Room {
            roomKey=roomKey.immutable();reasonCounts=Map.copyOf(reasonCounts);
            categoryPresence=Map.copyOf(categoryPresence);
            var normalizedHits=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
            for(var c:ComfortCategory.values())normalizedHits.put(c,perCategoryQualifyingHitCount.getOrDefault(c,0));
            perCategoryQualifyingHitCount=Map.copyOf(normalizedHits);
            if(enclosedBeds<0||interiorCellsConsidered<0||boundaryCellsConsidered<0||categoryPresence.size()!=ComfortCategory.values().length)
                throw new IllegalArgumentException("Invalid Comfort room");
        }
    }
    public ComfortSnapshot {
        reasonCounts=Map.copyOf(reasonCounts);
        var normalizedCoverage=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
        for(var c:ComfortCategory.values())normalizedCoverage.put(c,categoryCoverage.getOrDefault(c,0));
        categoryCoverage=Map.copyOf(normalizedCoverage);
        categories=Map.copyOf(categories);roomRecords=List.copyOf(roomRecords);
        if(roomRecords.size()>4096||assessedRooms<0||assessedEnclosedBeds<0||roomsAttempted<0||roomsAttempted>roomLimit
            ||blockInspections<0||blockInspections>sharedBlockLimit)throw new IllegalArgumentException("Invalid Comfort snapshot");
        if(residentialComfortPercent.isPresent()&&(!enabled||scanStatus!=Status.COMPLETE||condition!=Condition.OBSERVED))
            throw new IllegalArgumentException("Unauthoritative Comfort");
    }
    public static ComfortSnapshot unavailable(ObservationMetadata metadata) {
        var categories=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values())categories.put(c,new ComfortSettings.Category(c.defaultEnabled,c.defaultWeight));
        return new ComfortSnapshot(metadata,true,Status.UNAVAILABLE,Map.of(Reason.ROOM_DATA_INCOMPLETE,1),Condition.INCOMPLETE,
            OptionalInt.empty(),0,OptionalInt.empty(),0,75,OptionalDouble.empty(),OptionalDouble.empty(),Optional.empty(),
            Map.of(),categories,List.of(),0,512,0,262144,65536);
    }
    public void write(FriendlyByteBuf b) {
        metadata.write(b);b.writeBoolean(enabled);b.writeEnum(scanStatus);reasons(b,reasonCounts);b.writeEnum(condition);
        optionalInt(b,expectedRooms);b.writeVarInt(assessedRooms);optionalInt(b,expectedEnclosedBeds);b.writeVarInt(assessedEnclosedBeds);
        b.writeVarInt(enabledWeight);percent(b,residentialComfortPercent);percent(b,observedRoomComfortPercent);band(b,band);
        for(var c:ComfortCategory.values()) {
            b.writeVarInt(categoryCoverage.getOrDefault(c,0));var setting=categories.get(c);b.writeBoolean(setting.enabled());b.writeVarInt(setting.weight());
        }
        for(int v:new int[]{roomsAttempted,roomLimit,blockInspections,sharedBlockLimit,cellsPerRoomLimit})b.writeVarInt(v);
        b.writeVarInt(roomRecords.size());
        for(var room:roomRecords) {
            b.writeBlockPos(room.roomKey());
            b.writeVarInt(room.enclosedBeds());b.writeVarInt(room.interiorCellsConsidered());b.writeVarInt(room.boundaryCellsConsidered());
            b.writeEnum(room.scanStatus());reasons(b,room.reasonCounts());
            for(var c:ComfortCategory.values()){b.writeEnum(room.categoryPresence().get(c));b.writeVarInt(room.perCategoryQualifyingHitCount().getOrDefault(c,0));}
            for(int v:new int[]{room.ignoredDuplicateHits(),room.statePredicateFailures(),room.excludedBlocks(),room.presentWeight(),room.enabledWeight()})b.writeVarInt(v);
            percent(b,room.score());band(b,room.band());
        }
    }
    public static ComfortSnapshot read(FriendlyByteBuf b) {
        var metadata=ObservationMetadata.read(b);boolean enabled=b.readBoolean();var status=b.readEnum(Status.class);var reasons=reasons(b);var condition=b.readEnum(Condition.class);
        var expected=optionalInt(b);int assessed=number(b,4096);var beds=optionalInt(b);int assessedBeds=number(b,65536),weight=number(b,800);
        var score=percent(b);var observed=percent(b);var band=band(b);
        var coverage=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
        var settings=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values()){coverage.put(c,number(b,4096));settings.put(c,new ComfortSettings.Category(b.readBoolean(),number(b,100)));}
        int attempts=number(b,4096),limit=number(b,4096),blocks=number(b,1048576),blockLimit=number(b,1048576),cells=number(b,262144);
        int count=number(b,4096);var rooms=new ArrayList<Room>();
        for(int i=0;i<count;i++) {
            var key=b.readBlockPos();int enclosed=number(b,65536),interior=number(b,262144),boundary=number(b,262144);
            var rs=b.readEnum(Status.class);var rr=reasons(b);var presence=new EnumMap<ComfortCategory,Presence>(ComfortCategory.class);
            var hits=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
            for(var c:ComfortCategory.values()){presence.put(c,b.readEnum(Presence.class));hits.put(c,number(b,262144));}
            int duplicate=number(b,2097152),failed=number(b,2097152),excluded=number(b,262144),present=number(b,800),ew=number(b,800);
            rooms.add(new Room(key,enclosed,interior,boundary,rs,rr,presence,hits,duplicate,failed,excluded,present,ew,percent(b),band(b)));
        }
        return new ComfortSnapshot(metadata,enabled,status,reasons,condition,expected,assessed,beds,assessedBeds,weight,score,observed,band,
            coverage,settings,rooms,attempts,limit,blocks,blockLimit,cells);
    }
    private static int number(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid Comfort count");return n;}
    private static void optionalInt(FriendlyByteBuf b,OptionalInt n){b.writeBoolean(n.isPresent());if(n.isPresent())b.writeVarInt(n.getAsInt());}
    private static OptionalInt optionalInt(FriendlyByteBuf b){return b.readBoolean()?OptionalInt.of(number(b,65536)):OptionalInt.empty();}
    private static void percent(FriendlyByteBuf b,OptionalDouble v){b.writeBoolean(v.isPresent());if(v.isPresent())b.writeDouble(v.getAsDouble());}
    private static OptionalDouble percent(FriendlyByteBuf b){if(!b.readBoolean())return OptionalDouble.empty();double v=b.readDouble();if(!Double.isFinite(v)||v<0||v>100)throw new IllegalArgumentException("Invalid Comfort percent");return OptionalDouble.of(v);}
    private static void band(FriendlyByteBuf b,Optional<Band> band){b.writeBoolean(band.isPresent());band.ifPresent(b::writeEnum);}
    private static Optional<Band> band(FriendlyByteBuf b){return b.readBoolean()?Optional.of(b.readEnum(Band.class)):Optional.empty();}
    private static void reasons(FriendlyByteBuf b,Map<Reason,Integer> reasons){b.writeVarInt(reasons.size());new TreeMap<>(reasons).forEach((k,v)->{b.writeEnum(k);b.writeVarInt(v);});}
    private static Map<Reason,Integer> reasons(FriendlyByteBuf b){
        int count=number(b,Reason.values().length);var reasons=new EnumMap<Reason,Integer>(Reason.class);
        for(int i=0;i<count;i++){var reason=b.readEnum(Reason.class);int n=number(b,2097152);if(n==0||reasons.put(reason,n)!=null)throw new IllegalArgumentException("Invalid Comfort reasons");}
        return reasons;
    }
}
