package dev.conner.hometown.settlement;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Diagnostic-only timing/work counters captured from one normal fresh Ledger observation. Never persisted. */
public record LedgerPerformanceProfile(UUID settlementId,String townName,String dimension,BlockPos bellPosition,int radius,
        long generation,long observedGameTime,long elapsedNanos,int population,int enclosedBeds,
        int foodContainers,int foodStorageScanned,int roomsAttempted,int assessedRooms,int loadedChunks,int requiredChunks,
        int entitiesInspected,int entityLimit,int sharedBlockInspections,int sharedBlockLimit,
        int growingCandidateSections,int growingPaletteInspections,int growingBlockInspections) {
    public LedgerPerformanceProfile {
        Objects.requireNonNull(settlementId);Objects.requireNonNull(townName);Objects.requireNonNull(dimension);Objects.requireNonNull(bellPosition);
        bellPosition=bellPosition.immutable();
        if(radius<0||generation<0||observedGameTime<0||elapsedNanos<0||population<0||enclosedBeds<0||foodContainers<0||foodStorageScanned<0
                ||roomsAttempted<0||assessedRooms<0||loadedChunks<0||requiredChunks<0||entitiesInspected<0||entityLimit<0
                ||sharedBlockInspections<0||sharedBlockLimit<0||growingCandidateSections<0||growingPaletteInspections<0||growingBlockInspections<0
                ||entitiesInspected>entityLimit||sharedBlockInspections>sharedBlockLimit||loadedChunks>requiredChunks)
            throw new IllegalArgumentException("Invalid Ledger performance profile");
    }
    public double elapsedMillis(){return elapsedNanos/1_000_000.0d;}
}
