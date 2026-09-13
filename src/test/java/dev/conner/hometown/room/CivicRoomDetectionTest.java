package dev.conner.hometown.room;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CivicRoomDetectionTest {
    @Test void enclosedRoomCanBeResolvedFromInteriorWithoutBed() {
        RoomDetector.WorldView world = p -> {
            boolean within = p.getX() >= 0 && p.getX() <= 5 && p.getY() >= 0 && p.getY() <= 3
                    && p.getZ() >= 0 && p.getZ() <= 5;
            if (!within) return new RoomDetector.Cell(true, false, true, null);
            boolean boundary = p.getX() == 0 || p.getX() == 5 || p.getY() == 0 || p.getY() == 3
                    || p.getZ() == 0 || p.getZ() == 5;
            return new RoomDetector.Cell(true, boundary, false, null);
        };
        RoomDetector detector = new RoomDetector(world);
        var result = detector.detectInterior(new BlockPos(2, 1, 2));
        assertTrue(result.enclosed(), result.toString());
        assertEquals(32, result.interiorVolume());
        assertTrue(result.bedPositions().isEmpty());
        var geometry = detector.geometry(result);
        assertTrue(geometry.complete());
        assertEquals(32, geometry.interior().size());
        assertFalse(geometry.boundary().isEmpty());
    }

    @Test void unavailableInteriorFailsClosedWithoutFallbackScan() {
        RoomDetector detector = new RoomDetector(p -> RoomDetector.Cell.UNAVAILABLE);
        var result = detector.detectInterior(BlockPos.ZERO);
        assertFalse(result.enclosed());
        assertEquals(RoomFailureReason.CHUNK_UNAVAILABLE, result.failureReason());
        assertEquals(1, detector.inspectedCells());
    }
}
