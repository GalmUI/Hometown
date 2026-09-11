package dev.conner.hometown.room;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** One synchronous, scan-local session. Discard after the calling command/settlement scan. */
public final class RoomDetector {
    public record Limits(int maximumRoomVolume, int maximumHorizontalSearchDistance,
                         int maximumVerticalSearchDistance, int maximumInspectedCells) {
        public static final Limits DEFAULT = new Limits(4096, 32, 16, 65536);
        public Limits {
            if (maximumRoomVolume < 1 || maximumHorizontalSearchDistance < 1
                    || maximumVerticalSearchDistance < 1 || maximumInspectedCells < 1)
                throw new IllegalArgumentException("Room limits must be positive");
        }
    }
    public record Cell(boolean available, boolean boundary, boolean outside, BlockPos bedHead) {
        public Cell { if (bedHead != null) bedHead = bedHead.immutable(); }
        public static final Cell UNAVAILABLE = new Cell(false, false, false, null);
    }
    /** Implementations must never request chunks. Bed heads must identify intact two-part beds. */
    public interface WorldView { Cell cell(BlockPos position); }

    private final WorldView world;
    private final Limits limits;
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final Map<BlockPos, RoomDetectionResult> enclosedCells = new HashMap<>();
    private final Map<BlockPos, RoomDetectionResult> enclosedBeds = new HashMap<>();
    private int inspectedCells;
    public RoomDetector(WorldView world) { this(world, Limits.DEFAULT); }
    public RoomDetector(WorldView world, Limits limits) { this.world = world; this.limits = limits; }
    public int inspectedCells() { return inspectedCells; }

    /** Read-only adapter over a completed flood result; does not inspect the world again. */
    public RoomGeometry geometry(RoomDetectionResult result) {
        Set<BlockPos> interior=new HashSet<>(), boundary=new HashSet<>();
        enclosedCells.forEach((position,room)->{if(room==result)interior.add(position);});
        boolean complete=result.enclosed() && interior.size()==result.interiorVolume();
        for(BlockPos position:interior) for(Direction direction:Direction.values()) {
            BlockPos adjacent=position.relative(direction);
            if(interior.contains(adjacent))continue;
            Cell cell=cells.get(adjacent);
            if(cell!=null && cell.available() && cell.boundary())boundary.add(adjacent);
            else complete=false;
        }
        return new RoomGeometry(result.representativePosition(),result.bedPositions(),interior,boundary,complete);
    }

    private Cell read(BlockPos p) {
        Cell cached = cells.get(p);
        if (cached != null) return cached;
        if (inspectedCells >= limits.maximumInspectedCells()) return null;
        inspectedCells++;
        Cell cell = world.cell(p);
        cells.put(p.immutable(), cell);
        return cell;
    }

    public RoomDetectionResult detect(BlockPos bedPosition) {
        BlockPos bed = bedPosition.immutable();
        Cell origin = read(bed);
        if (origin == null) return failure(bed, RoomFailureReason.SCAN_BUDGET_EXCEEDED);
        if (!origin.available()) return failure(bed, RoomFailureReason.CHUNK_UNAVAILABLE);
        if (origin.bedHead() == null) return failure(bed, RoomFailureReason.NO_VALID_INTERIOR_START);
        bed = origin.bedHead();
        RoomDetectionResult cached = enclosedBeds.get(bed);
        if (cached != null) return cached;
        // Bed cells are traversable; a free cell over either half or beside the head is a seed.
        List<BlockPos> starts = new ArrayList<>();
        starts.add(bed.above());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = bed.relative(d);
            Cell c = read(adjacent);
            if (c == null) return failure(bed, RoomFailureReason.SCAN_BUDGET_EXCEEDED);
            if (bed.equals(c.bedHead())) starts.add(adjacent.above());
            starts.add(adjacent);
        }
        boolean unavailable = false;
        for (BlockPos start : starts) {
            Cell c = read(start);
            if (c == null) return failure(bed, RoomFailureReason.SCAN_BUDGET_EXCEEDED);
            if (!c.available()) { unavailable = true; continue; }
            if (c.boundary() || c.bedHead() != null) continue;
            cached = enclosedCells.get(start);
            if (cached != null) return cached;
            return flood(start);
        }
        return failure(bed, unavailable ? RoomFailureReason.CHUNK_UNAVAILABLE : RoomFailureReason.NO_VALID_INTERIOR_START);
    }

    private RoomDetectionResult flood(BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>(), interior = new HashSet<>(), beds = new HashSet<>();
        queue.add(start); visited.add(start);
        BlockPos representative = start;
        RoomFailureReason reason = RoomFailureReason.NONE;
        while (!queue.isEmpty()) {
            BlockPos p = queue.removeFirst();
            Cell c = read(p);
            if (c == null) { reason = RoomFailureReason.SCAN_BUDGET_EXCEEDED; break; }
            if (!c.available()) { reason = RoomFailureReason.CHUNK_UNAVAILABLE; break; }
            if (c.boundary()) continue;
            if (c.outside()) { reason = RoomFailureReason.ESCAPED_TO_OUTSIDE; break; }
            if (Math.abs((long)p.getX() - start.getX()) > limits.maximumHorizontalSearchDistance()
                    || Math.abs((long)p.getZ() - start.getZ()) > limits.maximumHorizontalSearchDistance()
                    || Math.abs((long)p.getY() - start.getY()) > limits.maximumVerticalSearchDistance()) {
                reason = RoomFailureReason.MAX_DISTANCE_EXCEEDED; break;
            }
            if (interior.size() >= limits.maximumRoomVolume()) { reason = RoomFailureReason.MAX_VOLUME_EXCEEDED; break; }
            interior.add(p);
            if (p.compareTo(representative) < 0) representative = p;
            if (c.bedHead() != null) beds.add(c.bedHead());
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (visited.add(next)) queue.addLast(next);
            }
        }
        RoomDetectionResult result = new RoomDetectionResult(reason == RoomFailureReason.NONE,
                representative, interior.size(), beds, reason);
        if (result.enclosed()) {
            interior.forEach(p -> enclosedCells.put(p, result));
            beds.forEach(p -> enclosedBeds.put(p, result));
        }
        return result;
    }
    private static RoomDetectionResult failure(BlockPos p, RoomFailureReason reason) {
        return new RoomDetectionResult(false, p, 0, Set.of(), reason);
    }
}
