package dev.conner.hometown.food;

import java.util.Set;

/** Scan-local counters, sent with the snapshot so the debug command and UI share evidence. */
public record FoodScanDiagnostics(Set<FoodScanReason> reasons, int settlementChunks, int loadedChunks,
        int unavailableChunks, int blockEntitiesInspected, int storageFound, int storageScanned,
        int duplicateInventoriesSkipped, int storageUnavailable, int lootContainersSkipped, int slotsInspected,
        FoodScanner.Limits limits, boolean populationComplete) {
    public FoodScanDiagnostics {
        reasons = Set.copyOf(reasons);
        java.util.Objects.requireNonNull(limits);
        if (reasons.contains(FoodScanReason.NONE) || settlementChunks < 0 || loadedChunks < 0 || unavailableChunks < 0
                || (long)loadedChunks + unavailableChunks != settlementChunks || blockEntitiesInspected < 0
                || storageFound < 0 || storageScanned < 0 || duplicateInventoriesSkipped < 0 || storageUnavailable < 0
                || lootContainersSkipped < 0 || slotsInspected < 0) throw new IllegalArgumentException("Invalid Food diagnostics");
    }
    public boolean scanLimitHit() { return reasons.contains(FoodScanReason.SCAN_LIMIT_REACHED); }
    public FoodScanReason primaryReason() {
        for (var reason : new FoodScanReason[]{FoodScanReason.NO_SETTLEMENT_DATA,FoodScanReason.INTERNAL_ERROR,
                FoodScanReason.SCAN_LIMIT_REACHED,FoodScanReason.LOOT_NOT_GENERATED,FoodScanReason.STORAGE_UNAVAILABLE,
                FoodScanReason.UNLOADED_CHUNKS,FoodScanReason.POPULATION_INCOMPLETE})
            if (reasons.contains(reason)) return reason;
        return FoodScanReason.NONE;
    }
    public static FoodScanDiagnostics empty(Set<FoodScanReason> reasons, boolean populationComplete) {
        return new FoodScanDiagnostics(reasons,0,0,0,0,0,0,0,0,0,0,FoodScanner.Limits.DEFAULT,populationComplete);
    }
}
