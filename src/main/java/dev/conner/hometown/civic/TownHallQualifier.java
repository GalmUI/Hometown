package dev.conner.hometown.civic;

import dev.conner.hometown.room.*;
import dev.conner.hometown.settlement.Settlement;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Explicit, loaded-only Town Hall validation. Never persists state or requests chunks. */
public final class TownHallQualifier {
    public enum Reason {
        QUALIFIED,
        ROOM_NOT_FOUND,
        ROOM_AMBIGUOUS,
        ROOM_INCOMPLETE,
        FLOOR_AREA_TOO_SMALL,
        NOT_ENOUGH_BOOKSHELVES,
        NO_LECTERN,
        NO_STORAGE,
        UNSAFE_LIGHTING
    }

    public record Result(boolean qualified, Reason reason, RoomGeometry room,
                         int usableFloorPositions, int bookshelves, int lecterns,
                         int storageBlocks, int darkFloorPositions) {
        public Result {
            Objects.requireNonNull(reason);
            if (qualified != (reason == Reason.QUALIFIED)) throw new IllegalArgumentException("Qualification/result mismatch");
        }
        public static Result failed(Reason reason) { return new Result(false, reason, null, 0, 0, 0, 0, 0); }
    }

    private TownHallQualifier() {}

    public static Result qualify(ServerLevel level, Settlement town, BlockPos markerPosition) {
        if (level == null || town == null || markerPosition == null
                || !town.dimension().equals(level.dimension()) || !town.contains(level.dimension(), markerPosition)) {
            return Result.failed(Reason.ROOM_NOT_FOUND);
        }
        if (chunk(level, markerPosition) == null) return Result.failed(Reason.ROOM_INCOMPLETE);

        LoadedRoomWorld world = new LoadedRoomWorld(level);
        RoomDetector detector = new RoomDetector(world);
        LinkedHashMap<BlockPos, RoomGeometry> rooms = new LinkedHashMap<>();
        boolean incomplete = false;

        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(markerPosition.immutable());
        for (Direction direction : Direction.values()) candidates.add(markerPosition.relative(direction).immutable());

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

        if (rooms.isEmpty()) return Result.failed(incomplete ? Reason.ROOM_INCOMPLETE : Reason.ROOM_NOT_FOUND);
        if (rooms.size() != 1) return Result.failed(Reason.ROOM_AMBIGUOUS);
        RoomGeometry room = rooms.values().iterator().next();

        LinkedHashSet<BlockPos> semanticPositions = new LinkedHashSet<>(room.boundary());
        semanticPositions.addAll(room.interior());
        int bookshelves = 0, lecterns = 0, storage = 0;
        for (BlockPos position : semanticPositions) {
            BlockState state = state(level, position);
            if (state == null) return Result.failed(Reason.ROOM_INCOMPLETE);
            if (state.is(TownHallRules.BOOKSHELVES)) bookshelves++;
            if (state.is(Blocks.LECTERN)) lecterns++;
            if (state.is(TownHallRules.STORAGE)) storage++;
        }

        int usable = 0, dark = 0;
        for (BlockPos feet : room.interior()) {
            BlockPos head = feet.above();
            BlockPos floor = feet.below();
            if (!room.interior().contains(head)) continue;
            BlockState feetState = state(level, feet);
            BlockState headState = state(level, head);
            BlockState floorState = state(level, floor);
            if (feetState == null || headState == null || floorState == null) return Result.failed(Reason.ROOM_INCOMPLETE);
            if (!feetState.getCollisionShape(world, feet).isEmpty() || !headState.getCollisionShape(world, head).isEmpty()) continue;
            if (!floorState.isFaceSturdy(world, floor, Direction.UP)) continue;
            usable++;
            if (level.getBrightness(LightLayer.BLOCK, feet) < TownHallRules.MIN_BLOCK_LIGHT) dark++;
        }

        Result snapshot = new Result(false, Reason.FLOOR_AREA_TOO_SMALL, room, usable, bookshelves, lecterns, storage, dark);
        if (usable < TownHallRules.MIN_USABLE_FLOOR_POSITIONS) return snapshot;
        if (bookshelves < TownHallRules.MIN_BOOKSHELVES)
            return new Result(false, Reason.NOT_ENOUGH_BOOKSHELVES, room, usable, bookshelves, lecterns, storage, dark);
        if (lecterns < 1) return new Result(false, Reason.NO_LECTERN, room, usable, bookshelves, lecterns, storage, dark);
        if (storage < 1) return new Result(false, Reason.NO_STORAGE, room, usable, bookshelves, lecterns, storage, dark);
        if (dark > 0) return new Result(false, Reason.UNSAFE_LIGHTING, room, usable, bookshelves, lecterns, storage, dark);
        return new Result(true, Reason.QUALIFIED, room, usable, bookshelves, lecterns, storage, dark);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static BlockState state(ServerLevel level, BlockPos position) {
        LevelChunk chunk = chunk(level, position);
        return chunk == null ? null : chunk.getBlockState(position);
    }
}
