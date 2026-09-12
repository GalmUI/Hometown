package dev.conner.hometown.food;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Loaded-only crop observation. Discovery completeness and maturity completeness are independent. */
public record FoodGrowingSnapshot(ObservationMetadata metadata, boolean enabled, Status scanStatus,
        Map<Reason,Integer> reasonCounts, int growingCropBlocks, int knownMatureCropBlocks,
        int maturityUnassessedCropBlocks, int cropFamilyCount, List<FamilyRecord> families,
        int chunksConsidered, int loadedChunks, int candidateSections, int paletteInspections,
        int blockInspections, int sharedBlockInspections, int sharedBlockLimit) {
    public enum Status { COMPLETE, PARTIAL, UNAVAILABLE, DISABLED }
    public enum Reason { UNLOADED_CHUNKS, SCAN_LIMIT_REACHED, INTERNAL_ERROR, NO_SETTLEMENT_DATA, MATURITY_UNASSESSED }
    public enum MaturityStatus { ASSESSED, UNASSESSED }
    public record FamilyRecord(ResourceLocation familyId,int growing,OptionalInt mature,MaturityStatus maturityStatus) {
        public FamilyRecord {
            Objects.requireNonNull(familyId); Objects.requireNonNull(mature); Objects.requireNonNull(maturityStatus);
            if(growing<0||mature.isPresent()&&(mature.getAsInt()<0||mature.getAsInt()>growing))throw new IllegalArgumentException("Invalid crop family counts");
            if((maturityStatus==MaturityStatus.ASSESSED)!=mature.isPresent())throw new IllegalArgumentException("Crop family maturity mismatch");
        }
    }
    public FoodGrowingSnapshot {
        Objects.requireNonNull(metadata); Objects.requireNonNull(scanStatus);
        reasonCounts=Map.copyOf(reasonCounts);families=List.copyOf(families);
        if(growingCropBlocks<0||knownMatureCropBlocks<0||knownMatureCropBlocks>growingCropBlocks||maturityUnassessedCropBlocks<0
                ||maturityUnassessedCropBlocks>growingCropBlocks||cropFamilyCount<0||cropFamilyCount!=families.size()
                ||chunksConsidered<0||loadedChunks<0||loadedChunks>chunksConsidered||candidateSections<0||paletteInspections<0||blockInspections<0
                ||sharedBlockInspections<0||sharedBlockLimit<0||sharedBlockInspections>sharedBlockLimit)
            throw new IllegalArgumentException("Invalid Growing snapshot");
    }
    public boolean discoveryComplete(){return scanStatus==Status.COMPLETE;}
    public boolean maturityComplete(){return maturityUnassessedCropBlocks==0;}
    public static FoodGrowingSnapshot unavailable(ObservationMetadata metadata) {
        return new FoodGrowingSnapshot(metadata,true,Status.UNAVAILABLE,Map.of(Reason.NO_SETTLEMENT_DATA,1),0,0,0,0,List.of(),0,0,0,0,0,0,0);
    }
    public void write(FriendlyByteBuf b) {
        metadata.write(b);b.writeBoolean(enabled);b.writeEnum(scanStatus);b.writeVarInt(reasonCounts.size());
        new TreeMap<>(reasonCounts).forEach((reason,count)->{b.writeEnum(reason);b.writeVarInt(count);});
        for(int value:new int[]{growingCropBlocks,knownMatureCropBlocks,maturityUnassessedCropBlocks,cropFamilyCount,chunksConsidered,loadedChunks,candidateSections,paletteInspections,blockInspections,sharedBlockInspections,sharedBlockLimit})b.writeVarInt(value);
        b.writeVarInt(families.size());
        for(var family:families){b.writeResourceLocation(family.familyId());b.writeVarInt(family.growing());b.writeEnum(family.maturityStatus());b.writeBoolean(family.mature().isPresent());if(family.mature().isPresent())b.writeVarInt(family.mature().getAsInt());}
    }
    public static FoodGrowingSnapshot read(FriendlyByteBuf b) {
        var metadata=ObservationMetadata.read(b);boolean enabled=b.readBoolean();var status=b.readEnum(Status.class);int reasonSize=b.readVarInt();
        if(reasonSize<0||reasonSize>Reason.values().length)throw new IllegalArgumentException("Invalid Growing reasons");
        var reasons=new EnumMap<Reason,Integer>(Reason.class);for(int i=0;i<reasonSize;i++){var reason=b.readEnum(Reason.class);int count=b.readVarInt();if(count<=0||reasons.put(reason,count)!=null)throw new IllegalArgumentException("Invalid Growing reason");}
        int growing=b.readVarInt(),mature=b.readVarInt(),unassessed=b.readVarInt(),familyCount=b.readVarInt(),chunks=b.readVarInt(),loaded=b.readVarInt(),sections=b.readVarInt(),palette=b.readVarInt(),blocks=b.readVarInt(),shared=b.readVarInt(),limit=b.readVarInt();
        int familySize=b.readVarInt();if(familySize<0||familySize>65536)throw new IllegalArgumentException("Invalid Growing family count");
        var families=new ArrayList<FamilyRecord>(familySize);for(int i=0;i<familySize;i++){var id=b.readResourceLocation();int count=b.readVarInt();var ms=b.readEnum(MaturityStatus.class);var value=b.readBoolean()?OptionalInt.of(b.readVarInt()):OptionalInt.empty();families.add(new FamilyRecord(id,count,value,ms));}
        if(familyCount!=familySize)throw new IllegalArgumentException("Growing family count mismatch");
        return new FoodGrowingSnapshot(metadata,enabled,status,reasons,growing,mature,unassessed,familyCount,families,chunks,loaded,sections,palette,blocks,shared,limit);
    }
}
