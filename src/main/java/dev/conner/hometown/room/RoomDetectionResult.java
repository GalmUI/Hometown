package dev.conner.hometown.room;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;

/** Complete counts only when enclosed; failed results contain partial observations. Never persisted. */
public record RoomDetectionResult(boolean enclosed, BlockPos representativePosition, int interiorVolume,
                                  Set<BlockPos> bedPositions, RoomFailureReason failureReason) {
    public RoomDetectionResult {
        representativePosition = representativePosition.immutable();
        bedPositions = bedPositions.stream().map(BlockPos::immutable).collect(Collectors.toUnmodifiableSet());
        if (interiorVolume < 0 || enclosed != (failureReason == RoomFailureReason.NONE))
            throw new IllegalArgumentException("Inconsistent room result");
    }
    public int bedCount() { return bedPositions.size(); }
}
