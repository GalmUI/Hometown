package dev.conner.hometown.safety;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.conner.hometown.observation.ObservationMetadata;

/** Separate entity and lighting evidence; there is deliberately no aggregate Safety score/status. */
public record SafetySnapshot(ObservationMetadata metadata, boolean enabled,
        Status entityScanStatus, Map<Reason,Integer> entityReasonCounts,
        Map<String,Integer> countsByThreatType, Map<String,Integer> countsByProtectorType,
        Map<String,Integer> collisions, int entitiesInspected, int rejectedEntities, int duplicateEntities,
        int entityLimit, int loadedChunks, int requiredChunks,
        Status lightingScanStatus, Map<Reason,Integer> lightingReasonCounts,
        int expectedBedSamples, int litBedSamples, int unlitBedSamples,
        OptionalDouble observedLightingPercent, OptionalDouble residentialLightingPercent,
        LightingCondition lightingCondition, int minimumBlockLight, int blocksInspected, int blockLimit,
        List<BlockPos> invalidSamples) {
    public enum Status { COMPLETE, PARTIAL, UNAVAILABLE, DISABLED }
    public enum Reason { UNLOADED_CHUNKS, ROOM_DATA_INCOMPLETE, SCAN_LIMIT_REACHED, INVALID_SAMPLE_POSITION,
        SAMPLE_OUT_OF_BOUNDS, NO_SETTLEMENT_DATA, INTERNAL_ERROR }
    public enum LightingCondition { OBSERVED, NO_ENCLOSED_BEDS, INCOMPLETE, DISABLED }
    public SafetySnapshot {
        Objects.requireNonNull(metadata);
        entityReasonCounts=Map.copyOf(entityReasonCounts); lightingReasonCounts=Map.copyOf(lightingReasonCounts);
        countsByThreatType=Map.copyOf(countsByThreatType); countsByProtectorType=Map.copyOf(countsByProtectorType);
        collisions=Map.copyOf(collisions); invalidSamples=invalidSamples.stream().map(BlockPos::immutable).toList();
        if (entitiesInspected<0 || entitiesInspected>entityLimit || blocksInspected<0 || blocksInspected>blockLimit
                || litBedSamples<0 || unlitBedSamples<0 || expectedBedSamples<litBedSamples+unlitBedSamples
                || invalidSamples.size()>32 || minimumBlockLight<0 || minimumBlockLight>15)
            throw new IllegalArgumentException("Invalid Safety observation");
        if (residentialLightingPercent.isPresent() && (!enabled || lightingScanStatus!=Status.COMPLETE || expectedBedSamples==0))
            throw new IllegalArgumentException("Unauthoritative lighting");
    }
    public int threatsObserved() { return countsByThreatType.values().stream().mapToInt(Integer::intValue).sum(); }
    public int protectorsObserved() { return countsByProtectorType.values().stream().mapToInt(Integer::intValue).sum(); }
    public int assessedBedSamples() { return litBedSamples+unlitBedSamples; }
    public int unassessedBedSamples() { return expectedBedSamples-assessedBedSamples(); }
    public static SafetySnapshot unavailable(ObservationMetadata m) {
        return new SafetySnapshot(m,true,Status.UNAVAILABLE,Map.of(Reason.NO_SETTLEMENT_DATA,1),Map.of(),Map.of(),Map.of(),
                0,0,0,4096,0,0,Status.UNAVAILABLE,Map.of(Reason.ROOM_DATA_INCOMPLETE,1),0,0,0,
                OptionalDouble.empty(),OptionalDouble.empty(),LightingCondition.INCOMPLETE,1,0,262144,List.of());
    }
    public void write(FriendlyByteBuf b) {
        metadata.write(b); b.writeBoolean(enabled); b.writeEnum(entityScanStatus); reasons(b,entityReasonCounts);
        types(b,countsByThreatType); types(b,countsByProtectorType); types(b,collisions);
        for(int n:new int[]{entitiesInspected,rejectedEntities,duplicateEntities,entityLimit,loadedChunks,requiredChunks}) b.writeVarInt(n);
        b.writeEnum(lightingScanStatus); reasons(b,lightingReasonCounts);
        for(int n:new int[]{expectedBedSamples,litBedSamples,unlitBedSamples}) b.writeVarInt(n);
        percent(b,observedLightingPercent); percent(b,residentialLightingPercent); b.writeEnum(lightingCondition);
        b.writeVarInt(minimumBlockLight); b.writeVarInt(blocksInspected); b.writeVarInt(blockLimit);
        b.writeVarInt(invalidSamples.size()); invalidSamples.forEach(b::writeBlockPos);
    }
    public static SafetySnapshot read(FriendlyByteBuf b) {
        var m=ObservationMetadata.read(b); boolean enabled=b.readBoolean(); var es=b.readEnum(Status.class); var er=reasons(b);
        var t=types(b); var p=types(b); var c=types(b);
        int inspected=b.readVarInt(),rejected=b.readVarInt(),duplicate=b.readVarInt(),el=b.readVarInt(),lc=b.readVarInt(),rc=b.readVarInt();
        var ls=b.readEnum(Status.class); var lr=reasons(b);
        int expected=b.readVarInt(),lit=b.readVarInt(),unlit=b.readVarInt();
        var observed=percent(b); var authoritative=percent(b); var condition=b.readEnum(LightingCondition.class);
        int threshold=b.readVarInt(),blocks=b.readVarInt(),bl=b.readVarInt(),count=size(b,32);
        var positions=new ArrayList<BlockPos>(); for(int i=0;i<count;i++) positions.add(b.readBlockPos());
        return new SafetySnapshot(m,enabled,es,er,t,p,c,inspected,rejected,duplicate,el,lc,rc,ls,lr,expected,lit,unlit,
            observed,authoritative,condition,threshold,blocks,bl,positions);
    }
    private static int size(FriendlyByteBuf b,int max) { int n=b.readVarInt(); if(n<0||n>max) throw new IllegalArgumentException("Oversized Safety data");return n; }
    private static void reasons(FriendlyByteBuf b,Map<Reason,Integer> m) { b.writeVarInt(m.size());new TreeMap<>(m).forEach((k,v)->{b.writeEnum(k);b.writeVarInt(v);}); }
    private static Map<Reason,Integer> reasons(FriendlyByteBuf b) { int n=size(b,Reason.values().length);var m=new EnumMap<Reason,Integer>(Reason.class);for(int i=0;i<n;i++){var k=b.readEnum(Reason.class);int v=b.readVarInt();if(v<=0||m.put(k,v)!=null)throw new IllegalArgumentException("Invalid reasons");}return m; }
    private static void types(FriendlyByteBuf b,Map<String,Integer> m) { b.writeVarInt(m.size());new TreeMap<>(m).forEach((k,v)->{b.writeUtf(k,256);b.writeVarInt(v);}); }
    private static Map<String,Integer> types(FriendlyByteBuf b) { int n=size(b,65536);var m=new TreeMap<String,Integer>();for(int i=0;i<n;i++){String k=b.readUtf(256);int v=b.readVarInt();if(v<=0||m.put(k,v)!=null)throw new IllegalArgumentException("Invalid type counts");}return m; }
    private static void percent(FriendlyByteBuf b,OptionalDouble v){b.writeBoolean(v.isPresent());if(v.isPresent())b.writeDouble(v.getAsDouble());}
    private static OptionalDouble percent(FriendlyByteBuf b){if(!b.readBoolean())return OptionalDouble.empty();double v=b.readDouble();if(!Double.isFinite(v)||v<0||v>100)throw new IllegalArgumentException("Invalid percentage");return OptionalDouble.of(v);}
}
