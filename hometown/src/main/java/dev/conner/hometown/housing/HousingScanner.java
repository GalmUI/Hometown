package dev.conner.hometown.housing;

import dev.conner.hometown.room.*;
import dev.conner.hometown.settlement.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Explicit synchronous Ledger scan only. No persistence or background work. */
public final class HousingScanner {
    public static final int MAX_ROOM_ATTEMPTS = 32;
    public static final int MAX_BEDS = 4096;
    private HousingScanner() {}
    public record Observation(HousingSnapshot snapshot, Set<BlockPos> enclosedBeds, List<RoomGeometry> rooms) {
        public Observation { enclosedBeds = Set.copyOf(enclosedBeds); rooms=List.copyOf(rooms); }
        public Observation(HousingSnapshot snapshot,Set<BlockPos> enclosedBeds) { this(snapshot,enclosedBeds,List.of()); }
    }
    public static HousingSnapshot scan(ServerLevel level, Settlement town, SettlementStats stats, int vertical) {
        return observe(level,town,stats,vertical).snapshot();
    }
    public static Observation observe(ServerLevel level, Settlement town, SettlementStats stats, int vertical) {
        if (level == null || stats.availability() != SettlementStats.Availability.COMPLETE)
            return new Observation(HousingSnapshot.unavailable(stats.population(), stats.beds()),Set.of());
        var bedScan = SettlementQueries.scanBeds(level, town.bellPosition(), town.radius(), vertical, MAX_BEDS + 1);
        var beds = bedScan.positions();
        if (!bedScan.complete() || beds.size() > MAX_BEDS || beds.size() != stats.beds()) return new Observation(HousingSnapshot.unavailable(stats.population(), stats.beds()),Set.of());
        RoomDetector detector = new RoomDetector(new LoadedRoomWorld(level));
        var enclosed = new HashSet<BlockPos>();
        var rooms=new ArrayList<RoomGeometry>();
        var snapshot = aggregate(stats.population(), beds, detector::detect, enclosed::add,room->rooms.add(detector.geometry(room)));
        var scopedRooms=rooms.stream().map(room->new RoomGeometry(room.key(),room.beds().stream().filter(enclosed::contains)
            .collect(java.util.stream.Collectors.toSet()),room.interior(),room.boundary(),room.complete())).toList();
        return new Observation(snapshot,enclosed,scopedRooms);
    }
    public static HousingSnapshot aggregate(int population, Collection<BlockPos> positions,
                                             Function<BlockPos, RoomDetectionResult> detect) {
        return aggregate(population,positions,detect,bed -> {},room -> {});
    }
    private static HousingSnapshot aggregate(int population, Collection<BlockPos> positions,
            Function<BlockPos, RoomDetectionResult> detect, java.util.function.Consumer<BlockPos> enclosedBed,
            java.util.function.Consumer<RoomDetectionResult> observedRoom) {
        var beds = positions.stream().map(BlockPos::immutable).distinct().sorted().toList();
        if (beds.size() > MAX_BEDS) return HousingSnapshot.unavailable(population, beds.size());
        Map<BlockPos, RoomDetectionResult> known = new HashMap<>();
        Map<BlockPos, RoomDetectionResult> failures = new HashMap<>();
        int attempts = 0;
        for (BlockPos bed : beds) {
            if (known.containsKey(bed) || attempts >= MAX_ROOM_ATTEMPTS) continue;
            attempts++;
            var room = detect.apply(bed);
            if (room.enclosed()) for (BlockPos member : room.bedPositions()) known.put(member, room);
            else failures.put(bed, room);
        }
        // A later seed may prove enclosure after an earlier distance-limited attempt.
        // Classify only after collecting successes so all relevant members receive that result.
        Set<BlockPos> rooms = new HashSet<>();
        int enclosed=0, unsealed=0, unknown=0;
        int[] roomCounts = new int[RoomDensity.values().length], bedCounts = new int[RoomDensity.values().length];
        for (BlockPos bed : beds) {
            var room = known.get(bed);
            if (room != null) {
                enclosed++;
                enclosedBed.accept(bed);
                int category = RoomDensity.forBeds(room.bedCount()).ordinal();
                bedCounts[category]++;
                if (rooms.add(room.representativePosition())) { roomCounts[category]++; observedRoom.accept(room); }
            } else {
                var failure = failures.get(bed);
                if (failure == null || failure.failureReason() == RoomFailureReason.CHUNK_UNAVAILABLE
                        || failure.failureReason() == RoomFailureReason.SCAN_BUDGET_EXCEEDED) unknown++;
                else unsealed++;
            }
        }
        return new HousingSnapshot(population, beds.size(), enclosed, unsealed, rooms.size(), roomCounts[0],
                roomCounts[1], bedCounts[1], unknown, unknown == 0, roomCounts[2], roomCounts[3], bedCounts[0], bedCounts[2], bedCounts[3],
                HousingSnapshot.privacy(unknown == 0,enclosed,bedCounts[0],bedCounts[1],bedCounts[2],bedCounts[3]));
    }
}
