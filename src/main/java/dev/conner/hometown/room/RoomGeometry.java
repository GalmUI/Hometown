package dev.conner.hometown.room;

import java.util.Set;
import net.minecraft.core.BlockPos;

/** Proven geometry copied from the existing detector; server-only and never serialized. */
public record RoomGeometry(BlockPos key, Set<BlockPos> beds, Set<BlockPos> interior,
        Set<BlockPos> boundary, boolean complete) {
    public RoomGeometry {
        key=key.immutable();
        beds=copy(beds);interior=copy(interior);boundary=copy(boundary);
    }
    private static Set<BlockPos> copy(Set<BlockPos> positions) {
        return positions.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
