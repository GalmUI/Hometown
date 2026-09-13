package dev.conner.hometown.civic;

import dev.conner.hometown.room.LoadedRoomWorld;
import dev.conner.hometown.room.RoomDetectionResult;
import dev.conner.hometown.room.RoomDetector;
import dev.conner.hometown.room.RoomFailureReason;
import dev.conner.hometown.room.RoomGeometry;
import dev.conner.hometown.settlement.Settlement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Explicit, loaded-only Animal Farm validation. Never opens inventories, persists state, or requests chunks. */
public final class AnimalFarmQualifier {
    public enum Reason {
        QUALIFIED,
        ROOM_NOT_FOUND,
        ROOM_AMBIGUOUS,
        ROOM_INCOMPLETE,
        NO_STORAGE,
        NO_LOOM,
        NOT_ENOUGH_PADDOCK_ATTACHMENTS,
        NOT_ENOUGH_PADDOCK_BARRIERS,
        PADDOCK_OPEN,
        PADDOCK_INCOMPLETE,
        PADDOCK_TRACE_LIMIT_REACHED,
        PADDOCK_SCAN_LIMIT_REACHED
    }

    private enum EnclosureStatus { ENCLOSED, OPEN, LIMIT_REACHED }

    public record Result(boolean qualified, Reason reason, RoomGeometry room,
                         int storageBlocks, int looms, int paddockAttachments,
                         int paddockBarriers, boolean paddockEnclosed) {
        public Result {
            Objects.requireNonNull(reason);
            if (qualified != (reason == Reason.QUALIFIED)) {
                throw new IllegalArgumentException("Qualification/result mismatch");
            }
        }

        public static Result failed(Reason reason) {
            return new Result(false, reason, null, 0, 0, 0, 0, false);
        }
    }

    private record Trace(Set<BlockPos> barriers, boolean incomplete, boolean limitReached) {}
    private record Cell(int x, int z) {}

    private AnimalFarmQualifier() {}

    public static Result qualify(ServerLevel level, Settlement town, BlockPos markerPosition) {
        if (level == null || town == null || markerPosition == null
                || !town.dimension().equals(level.dimension()) || !town.contains(level.dimension(), markerPosition)) {
            return Result.failed(Reason.ROOM_NOT_FOUND);
        }
        if (chunk(level, markerPosition) == null) return Result.failed(Reason.ROOM_INCOMPLETE);

        RoomResolution roomResolution = resolveRoom(level, markerPosition);
        if (roomResolution.reason != null) return Result.failed(roomResolution.reason);
        RoomGeometry room = roomResolution.room;

        LinkedHashSet<BlockPos> semanticPositions = new LinkedHashSet<>(room.boundary());
        semanticPositions.addAll(room.interior());
        int storage = 0;
        int looms = 0;
        for (BlockPos position : semanticPositions) {
            BlockState state = state(level, position);
            if (state == null) return Result.failed(Reason.ROOM_INCOMPLETE);
            if (state.is(StorageRules.STORAGE)) storage++;
            if (state.is(Blocks.LOOM)) looms++;
        }
        if (storage < AnimalFarmRules.MIN_STORAGE_BLOCKS) {
            return new Result(false, Reason.NO_STORAGE, room, storage, looms, 0, 0, false);
        }
        if (looms < AnimalFarmRules.MIN_LOOMS) {
            return new Result(false, Reason.NO_LOOM, room, storage, looms, 0, 0, false);
        }

        LoadedRoomWorld attachmentWorld = new LoadedRoomWorld(level);
        AttachmentScan attachmentScan = scanAttachments(room, position -> {
            if (!town.contains(level.dimension(), position)) return AttachmentCell.EMPTY;
            BlockState block = state(level, position);
            if (block == null) return AttachmentCell.UNAVAILABLE;
            if (block.is(AnimalFarmRules.PADDOCK_BARRIERS)) return AttachmentCell.BARRIER;
            RoomDetector.Cell cell = attachmentWorld.cell(position);
            return !cell.available() ? AttachmentCell.UNAVAILABLE
                    : cell.boundary() ? AttachmentCell.BUILDING : AttachmentCell.EMPTY;
        });
        Set<BlockPos> attachments = attachmentScan.attachments();

        int bestAttachments = 0;
        int bestBarriers = 0;
        boolean anyOpen = false;
        boolean anyIncomplete = attachmentScan.incomplete();
        boolean traceLimit = false;
        boolean scanLimit = false;
        Set<BlockPos> visitedComponents = new HashSet<>();
        List<BlockPos> orderedAttachments = new ArrayList<>(attachments);
        orderedAttachments.sort(BlockPos::compareTo);

        for (BlockPos attachment : orderedAttachments) {
            if (visitedComponents.contains(attachment)) continue;
            Trace trace = trace(level, town, attachment);
            visitedComponents.addAll(trace.barriers());
            anyIncomplete |= trace.incomplete();
            traceLimit |= trace.limitReached();

            int componentAttachments = 0;
            for (BlockPos candidate : attachments) if (trace.barriers().contains(candidate)) componentAttachments++;
            bestAttachments = Math.max(bestAttachments, componentAttachments);
            bestBarriers = Math.max(bestBarriers, trace.barriers().size());

            EnclosureStatus enclosure = enclosureStatus(attachmentScan.building(), trace.barriers());
            if (enclosure == EnclosureStatus.LIMIT_REACHED) scanLimit = true;
            if (enclosure == EnclosureStatus.OPEN) anyOpen = true;

            if (!attachmentScan.incomplete() && !trace.incomplete() && !trace.limitReached()
                    && componentAttachments >= AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS
                    && trace.barriers().size() >= AnimalFarmRules.MIN_PADDOCK_BARRIERS
                    && enclosure == EnclosureStatus.ENCLOSED) {
                return new Result(true, Reason.QUALIFIED, room, storage, looms,
                        componentAttachments, trace.barriers().size(), true);
            }
        }

        if (traceLimit) {
            return new Result(false, Reason.PADDOCK_TRACE_LIMIT_REACHED, room, storage, looms,
                    bestAttachments, bestBarriers, false);
        }
        if (scanLimit) {
            return new Result(false, Reason.PADDOCK_SCAN_LIMIT_REACHED, room, storage, looms,
                    bestAttachments, bestBarriers, false);
        }
        if (anyIncomplete) {
            return new Result(false, Reason.PADDOCK_INCOMPLETE, room, storage, looms,
                    bestAttachments, bestBarriers, false);
        }
        Reason paddockReason = paddockRuleReason(bestAttachments, bestBarriers, !anyOpen && !attachments.isEmpty());
        if (bestAttachments >= AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS
                && bestBarriers >= AnimalFarmRules.MIN_PADDOCK_BARRIERS) {
            paddockReason = Reason.PADDOCK_OPEN;
        }
        return new Result(false, paddockReason, room, storage, looms,
                bestAttachments, bestBarriers, false);
    }

    private static RoomResolution resolveRoom(ServerLevel level, BlockPos markerPosition) {
        LoadedRoomWorld world = new LoadedRoomWorld(level);
        RoomDetector detector = new RoomDetector(world);
        LinkedHashMap<BlockPos, RoomGeometry> rooms = new LinkedHashMap<>();
        boolean incomplete = false;

        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(markerPosition.immutable());
        for (Direction direction : Direction.values()) {
            candidates.add(markerPosition.relative(direction).immutable());
            candidates.add(markerPosition.relative(direction, 2).immutable());
        }
        for (BlockPos candidate : candidates) {
            RoomDetectionResult detected = detector.detectInterior(candidate);
            if (detected.enclosed()) {
                RoomGeometry geometry = detector.geometry(detected);
                if (!geometry.complete()) incomplete = true;
                else rooms.putIfAbsent(geometry.key(), geometry);
            } else if (detected.failureReason() == RoomFailureReason.CHUNK_UNAVAILABLE
                    || detected.failureReason() == RoomFailureReason.SCAN_BUDGET_EXCEEDED) {
                incomplete = true;
            }
        }
        if (rooms.isEmpty()) {
            return new RoomResolution(null, incomplete ? Reason.ROOM_INCOMPLETE : Reason.ROOM_NOT_FOUND);
        }
        if (rooms.size() != 1) return new RoomResolution(null, Reason.ROOM_AMBIGUOUS);
        return new RoomResolution(rooms.values().iterator().next(), null);
    }

    private record RoomResolution(RoomGeometry room, Reason reason) {}

    enum AttachmentCell { EMPTY, BUILDING, BARRIER, UNAVAILABLE }
    record AttachmentScan(Set<BlockPos> attachments, Set<BlockPos> building, boolean incomplete) {}

    /** The detector boundary is an interior-facing shell, not the complete exterior facade.
     * Extend it once through actual boundary blocks (corners/porch), never through air or fences.
     * Furniture can hide the floor beneath it, so include one level down, but never below the
     * detected floor. This recovers foundation corners without treating surrounding ground as a building.
     * Only those proven blocks may also close the paddock; the search halo itself cannot. */
    static AttachmentScan scanAttachments(RoomGeometry room, Function<BlockPos, AttachmentCell> read) {
        Set<BlockPos> building = new LinkedHashSet<>(room.boundary());
        building.addAll(room.interior());
        Set<BlockPos> attachments = new LinkedHashSet<>();
        Set<BlockPos> skirt = new LinkedHashSet<>();
        var cells = new LinkedHashMap<BlockPos, AttachmentCell>();
        int floorY = building.stream().mapToInt(BlockPos::getY).min().orElse(Integer.MAX_VALUE);
        for (BlockPos boundary : room.boundary()) {
            for (int dy = -1; dy <= 0; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = boundary.offset(dx, dy, dz).immutable();
                if (candidate.getY() < floorY || building.contains(candidate)) continue;
                AttachmentCell cell = cells.computeIfAbsent(candidate, read);
                if (cell == AttachmentCell.BARRIER && dy == 0 && Math.abs(dx) + Math.abs(dz) == 1) attachments.add(candidate);
                if (cell == AttachmentCell.BUILDING) skirt.add(candidate);
            }
        }
        building.addAll(skirt);
        for (BlockPos edge : skirt) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = edge.relative(direction).immutable();
                if (building.contains(candidate)) continue;
                if (cells.computeIfAbsent(candidate, read) == AttachmentCell.BARRIER) attachments.add(candidate);
            }
        }
        return new AttachmentScan(Set.copyOf(attachments), Set.copyOf(building),
                cells.containsValue(AttachmentCell.UNAVAILABLE));
    }

    private static Trace trace(ServerLevel level, Settlement town, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LinkedHashSet<BlockPos> barriers = new LinkedHashSet<>();
        boolean incomplete = false;
        queue.add(start.immutable());

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!barriers.add(current)) continue;
            if (barriers.size() > AnimalFarmRules.MAX_PADDOCK_BARRIERS) {
                return new Trace(Set.copyOf(barriers), incomplete, true);
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = current.relative(direction);
                if (!town.contains(level.dimension(), neighbor) || barriers.contains(neighbor)) continue;
                BlockState state = state(level, neighbor);
                if (state == null) {
                    incomplete = true;
                    continue;
                }
                if (state.is(AnimalFarmRules.PADDOCK_BARRIERS)) queue.addLast(neighbor.immutable());
            }
        }
        return new Trace(Set.copyOf(barriers), incomplete, false);
    }

    static Reason paddockRuleReason(int attachments, int barriers, boolean enclosed) {
        if (attachments < AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS) return Reason.NOT_ENOUGH_PADDOCK_ATTACHMENTS;
        if (barriers < AnimalFarmRules.MIN_PADDOCK_BARRIERS) return Reason.NOT_ENOUGH_PADDOCK_BARRIERS;
        if (!enclosed) return Reason.PADDOCK_OPEN;
        return Reason.QUALIFIED;
    }

    static boolean hasEnclosedPaddock(Set<BlockPos> buildingPositions, Set<BlockPos> fencePositions) {
        return enclosureStatus(buildingPositions, fencePositions) == EnclosureStatus.ENCLOSED;
    }

    private static EnclosureStatus enclosureStatus(RoomGeometry room, Set<BlockPos> fencePositions) {
        LinkedHashSet<BlockPos> building = new LinkedHashSet<>(room.boundary());
        building.addAll(room.interior());
        return enclosureStatus(building, fencePositions);
    }

    private static EnclosureStatus enclosureStatus(Set<BlockPos> buildingPositions, Set<BlockPos> fencePositions) {
        if (buildingPositions.isEmpty() || fencePositions.isEmpty()) return EnclosureStatus.OPEN;
        Set<Cell> blocked = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos position : buildingPositions) {
            Cell cell = new Cell(position.getX(), position.getZ());
            blocked.add(cell);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        for (BlockPos position : fencePositions) {
            Cell cell = new Cell(position.getX(), position.getZ());
            blocked.add(cell);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        minX--; maxX++; minZ--; maxZ++;
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        if (width <= 0L || depth <= 0L || width * depth > AnimalFarmRules.MAX_ENCLOSURE_CELLS) {
            return EnclosureStatus.LIMIT_REACHED;
        }

        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Cell> outside = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            addOutside(queue, outside, blocked, new Cell(x, minZ));
            addOutside(queue, outside, blocked, new Cell(x, maxZ));
        }
        for (int z = minZ; z <= maxZ; z++) {
            addOutside(queue, outside, blocked, new Cell(minX, z));
            addOutside(queue, outside, blocked, new Cell(maxX, z));
        }
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            for (int[] step : CARDINAL_STEPS) {
                Cell next = new Cell(cell.x() + step[0], cell.z() + step[1]);
                if (next.x() < minX || next.x() > maxX || next.z() < minZ || next.z() > maxZ) continue;
                addOutside(queue, outside, blocked, next);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Cell cell = new Cell(x, z);
                if (!blocked.contains(cell) && !outside.contains(cell)) return EnclosureStatus.ENCLOSED;
            }
        }
        return EnclosureStatus.OPEN;
    }

    private static final int[][] CARDINAL_STEPS = {{1,0},{-1,0},{0,1},{0,-1}};

    private static void addOutside(ArrayDeque<Cell> queue, Set<Cell> outside, Set<Cell> blocked, Cell cell) {
        if (!blocked.contains(cell) && outside.add(cell)) queue.addLast(cell);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static BlockState state(ServerLevel level, BlockPos position) {
        LevelChunk chunk = chunk(level, position);
        return chunk == null ? null : chunk.getBlockState(position);
    }
}
