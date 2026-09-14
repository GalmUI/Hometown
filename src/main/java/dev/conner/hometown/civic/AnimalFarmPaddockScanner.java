package dev.conner.hometown.civic;

import dev.conner.hometown.room.LoadedRoomWorld;
import dev.conner.hometown.room.RoomDetector;
import dev.conner.hometown.room.RoomGeometry;
import dev.conner.hometown.settlement.Settlement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Derives the actual enclosed paddock columns from the same room attachment rules used by
 * {@link AnimalFarmQualifier}. It never requests chunks and fails closed on incomplete data.
 */
final class AnimalFarmPaddockScanner {
    private record Cell(int x, int z) {}
    private record Trace(Set<BlockPos> barriers, boolean incomplete, boolean limitReached) {}

    record Area(Set<Long> interiorColumns, int minX, int maxX, int minZ, int maxZ) {
        Area {
            interiorColumns = Set.copyOf(interiorColumns);
            if (interiorColumns.isEmpty() || minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid paddock area");
            }
        }

        boolean contains(BlockPos position) {
            return interiorColumns.contains(key(position.getX(), position.getZ()));
        }

        boolean fullyLoaded(ServerLevel level) {
            HashSet<Long> checkedChunks = new HashSet<>();
            for (long column : interiorColumns) {
                int x = (int)(column >> 32);
                int z = (int)column;
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = key(chunkX, chunkZ);
                if (checkedChunks.add(chunkKey)
                        && level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
            }
            return true;
        }
    }

    private AnimalFarmPaddockScanner() {}

    static Optional<Area> resolve(ServerLevel level, Settlement town, AnimalFarmQualifier.Result result) {
        if (level == null || town == null || result == null || !result.qualified() || result.room() == null) {
            return Optional.empty();
        }
        RoomGeometry room = result.room();
        LoadedRoomWorld attachmentWorld = new LoadedRoomWorld(level);
        AnimalFarmQualifier.AttachmentScan scan = AnimalFarmQualifier.scanAttachments(room, position -> {
            if (!town.contains(level.dimension(), position)) return AnimalFarmQualifier.AttachmentCell.EMPTY;
            BlockState block = state(level, position);
            if (block == null) return AnimalFarmQualifier.AttachmentCell.UNAVAILABLE;
            if (block.is(AnimalFarmRules.PADDOCK_BARRIERS)) return AnimalFarmQualifier.AttachmentCell.BARRIER;
            RoomDetector.Cell cell = attachmentWorld.cell(position);
            return !cell.available() ? AnimalFarmQualifier.AttachmentCell.UNAVAILABLE
                    : cell.boundary() ? AnimalFarmQualifier.AttachmentCell.BUILDING
                    : AnimalFarmQualifier.AttachmentCell.EMPTY;
        });
        if (scan.incomplete() || scan.attachments().isEmpty()) return Optional.empty();

        ArrayList<BlockPos> ordered = new ArrayList<>(scan.attachments());
        ordered.sort(BlockPos::compareTo);
        HashSet<BlockPos> visited = new HashSet<>();
        for (BlockPos attachment : ordered) {
            if (visited.contains(attachment)) continue;
            Trace trace = trace(level, town, attachment);
            visited.addAll(trace.barriers());
            if (trace.incomplete() || trace.limitReached()) continue;

            int attachmentCount = 0;
            for (BlockPos candidate : scan.attachments()) {
                if (trace.barriers().contains(candidate)) attachmentCount++;
            }
            if (attachmentCount < AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS
                    || trace.barriers().size() < AnimalFarmRules.MIN_PADDOCK_BARRIERS) continue;

            Optional<Area> area = enclosedArea(scan.building(), trace.barriers());
            if (area.isPresent() && area.get().fullyLoaded(level)) return area;
        }
        return Optional.empty();
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
                BlockState block = state(level, neighbor);
                if (block == null) {
                    incomplete = true;
                    continue;
                }
                if (block.is(AnimalFarmRules.PADDOCK_BARRIERS)) queue.addLast(neighbor.immutable());
            }
        }
        return new Trace(Set.copyOf(barriers), incomplete, false);
    }

    private static Optional<Area> enclosedArea(Set<BlockPos> building, Set<BlockPos> barriers) {
        if (building.isEmpty() || barriers.isEmpty()) return Optional.empty();
        HashSet<Cell> blocked = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos position : building) {
            Cell cell = new Cell(position.getX(), position.getZ());
            blocked.add(cell);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        for (BlockPos position : barriers) {
            Cell cell = new Cell(position.getX(), position.getZ());
            blocked.add(cell);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        minX--; maxX++; minZ--; maxZ++;
        long width = (long)maxX - minX + 1L;
        long depth = (long)maxZ - minZ + 1L;
        if (width <= 0 || depth <= 0 || width * depth > AnimalFarmRules.MAX_ENCLOSURE_CELLS) {
            return Optional.empty();
        }

        ArrayDeque<Cell> queue = new ArrayDeque<>();
        HashSet<Cell> outside = new HashSet<>();
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

        LinkedHashSet<Long> interior = new LinkedHashSet<>();
        int insideMinX = Integer.MAX_VALUE, insideMaxX = Integer.MIN_VALUE;
        int insideMinZ = Integer.MAX_VALUE, insideMaxZ = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Cell cell = new Cell(x, z);
                if (blocked.contains(cell) || outside.contains(cell)) continue;
                interior.add(key(x, z));
                insideMinX = Math.min(insideMinX, x); insideMaxX = Math.max(insideMaxX, x);
                insideMinZ = Math.min(insideMinZ, z); insideMaxZ = Math.max(insideMaxZ, z);
            }
        }
        if (interior.isEmpty()) return Optional.empty();
        return Optional.of(new Area(interior, insideMinX, insideMaxX, insideMinZ, insideMaxZ));
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

    private static long key(int x, int z) {
        return ((long)x << 32) ^ (z & 0xffffffffL);
    }
}
