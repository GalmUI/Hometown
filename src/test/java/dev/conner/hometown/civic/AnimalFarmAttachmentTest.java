package dev.conner.hometown.civic;

import dev.conner.hometown.room.RoomDetector;
import dev.conner.hometown.room.RoomGeometry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalFarmAttachmentTest {
    private final Set<BlockPos> solid = new HashSet<>();

    private RoomGeometry barn() {
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
            for (int y = 64; y <= 68; y++) {
                if (x == 0 || x == 4 || z == 0 || z == 4 || y == 64 || y == 68)
                    solid.add(new BlockPos(x, y, z));
            }
        }
        RoomDetector detector = new RoomDetector(p -> new RoomDetector.Cell(true,
                solid.contains(p), p.getY() > 68, null));
        var result = detector.detectInterior(new BlockPos(2, 65, 2));
        assertTrue(result.enclosed());
        RoomGeometry room = detector.geometry(result);
        assertTrue(room.complete());
        return room;
    }

    private Set<BlockPos> paddock(int y) {
        Set<BlockPos> fence = new HashSet<>();
        for (int z = -1; z >= -6; z--) {
            fence.add(new BlockPos(0, y, z));
            fence.add(new BlockPos(4, y, z));
        }
        for (int x = 0; x <= 4; x++) fence.add(new BlockPos(x, y, -6));
        // Sixteen connected barriers, with a gate represented by the same barrier classification.
        fence.add(new BlockPos(5, y, -6));
        return fence;
    }

    private AnimalFarmQualifier.AttachmentScan scan(RoomGeometry room, Set<BlockPos> fence) {
        return AnimalFarmQualifier.scanAttachments(room, p -> fence.contains(p)
                ? AnimalFarmQualifier.AttachmentCell.BARRIER : solid.contains(p)
                ? AnimalFarmQualifier.AttachmentCell.BUILDING : AnimalFarmQualifier.AttachmentCell.EMPTY);
    }

    @Test void groundLevelFencesAtOmittedFoundationCornersQualify() {
        RoomGeometry room = barn();
        assertFalse(room.boundary().contains(new BlockPos(0, 64, 0)));
        Set<BlockPos> fence = paddock(64);
        var scan = scan(room, fence);
        assertEquals(Set.of(new BlockPos(0, 64, -1), new BlockPos(4, 64, -1)), scan.attachments());
        assertFalse(scan.incomplete());
        assertEquals(AnimalFarmQualifier.Reason.QUALIFIED, AnimalFarmQualifier.paddockRuleReason(
                scan.attachments().size(), fence.size(),
                AnimalFarmQualifier.hasEnclosedPaddock(scan.building(), fence)));
    }

    @Test void wallCornerAndOneBlockPorchEdgesAreRecognized() {
        RoomGeometry room = barn();
        assertEquals(2, scan(room, paddock(65)).attachments().size());
        for (int x = 0; x <= 4; x++) solid.add(new BlockPos(x, 65, -1));
        Set<BlockPos> fence = new HashSet<>();
        for (BlockPos p : paddock(65)) fence.add(p.offset(0, 0, -1));
        var scan = scan(room, fence);
        assertEquals(2, scan.attachments().size());
        assertTrue(AnimalFarmQualifier.hasEnclosedPaddock(scan.building(), fence));
    }

    @Test void nearbyClosedPenAcrossAirGapDoesNotAttach() {
        RoomGeometry room = barn();
        Set<BlockPos> fence = new HashSet<>();
        for (BlockPos p : paddock(64)) fence.add(p.offset(0, 0, -1));
        for (int x = 0; x <= 4; x++) fence.add(new BlockPos(x, 64, -2));
        assertTrue(AnimalFarmQualifier.hasEnclosedPaddock(room.boundary(), fence));
        assertTrue(scan(room, fence).attachments().isEmpty());
    }

    @Test void skirtDoesNotFillAirGapsOrRepairAnOpenPaddock() {
        RoomGeometry room = barn();
        Set<BlockPos> fence = paddock(64);
        fence.remove(new BlockPos(2, 64, -6));
        var scan = scan(room, fence);
        assertEquals(2, scan.attachments().size());
        assertFalse(AnimalFarmQualifier.hasEnclosedPaddock(scan.building(), fence));
        assertFalse(scan.building().contains(new BlockPos(2, 64, -1)));
        solid.remove(new BlockPos(0, 64, 0));
        assertEquals(1, scan(room, fence).attachments().size());
    }

    @Test void solidExtensionCannotRecursivelyReachADistantPen() {
        RoomGeometry room = barn();
        for (int z = -1; z >= -10; z--) for (int x = 0; x <= 4; x++)
            solid.add(new BlockPos(x, 65, z));
        Set<BlockPos> fence = new HashSet<>();
        for (BlockPos p : paddock(65)) fence.add(p.offset(0, 0, -10));
        assertTrue(scan(room, fence).attachments().isEmpty());
    }

    @Test void unavailableSkirtIsIncompleteAndEveryReadIsBoundedAndUnique() {
        RoomGeometry room = barn();
        Set<BlockPos> reads = new HashSet<>();
        var scan = AnimalFarmQualifier.scanAttachments(room, p -> {
            assertTrue(reads.add(p), "Duplicate world read: " + p);
            assertTrue(room.boundary().stream().anyMatch(b -> b.getY() == p.getY()
                    && Math.abs(b.getX() - p.getX()) <= 2 && Math.abs(b.getZ() - p.getZ()) <= 2));
            return p.equals(new BlockPos(0, 64, 0)) ? AnimalFarmQualifier.AttachmentCell.UNAVAILABLE
                    : solid.contains(p) ? AnimalFarmQualifier.AttachmentCell.BUILDING
                    : AnimalFarmQualifier.AttachmentCell.EMPTY;
        });
        assertTrue(scan.incomplete());
    }
}
