package dev.conner.hometown.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared founding/ledger queries. Never obtain or request an unloaded world chunk. */
public final class SettlementQueries {
    private SettlementQueries() {}

    public static AABB bounds(BlockPos bell, int radius, int vertical) {
        return new AABB(bell).inflate(radius, vertical, radius);
    }

    public static List<Villager> residents(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(Villager.class, bounds,
                v -> v.getType() == EntityType.VILLAGER && v.isAlive()
                        && bounds.contains(v.position()) && loaded(level, v.blockPosition()));
    }

    public static int countBeds(ServerLevel level, BlockPos bell, int radius, int vertical, int limit) {
        return bedPositions(level, bell, radius, vertical, limit).size();
    }

    public record BedScan(List<BlockPos> positions, boolean complete) {
        public BedScan { positions = List.copyOf(positions); }
    }
    public static List<BlockPos> bedPositions(ServerLevel level, BlockPos bell, int radius, int vertical, int limit) {
        return scanBeds(level, bell, radius, vertical, limit).positions();
    }
    public static BedScan scanBeds(ServerLevel level, BlockPos bell, int radius, int vertical, int limit) {
        if (limit <= 0) return new BedScan(List.of(), true);
        AABB bounds = bounds(bell, radius, vertical);
        var beds = new java.util.LinkedHashSet<BlockPos>();
        boolean complete = true;
        for (int x = (bell.getX() - radius) >> 4; x <= (bell.getX() + radius) >> 4; x++) {
            for (int z = (bell.getZ() - radius) >> 4; z <= (bell.getZ() + radius) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) { complete = false; continue; }
                try (var records = level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME),
                        new ChunkPos(x, z), PoiManager.Occupancy.ANY)) {
                    var iterator = records.filter(p -> bounds.contains(Vec3.atCenterOf(p.getPos()))).iterator();
                    while (iterator.hasNext()) {
                        BlockPos pos = iterator.next().getPos();
                        if (!loaded(level, pos)) { complete = false; continue; }
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
                                && !loaded(level, pos.relative(state.getValue(BedBlock.FACING).getOpposite()))) {
                            complete = false; continue;
                        }
                        if (validBed(level, pos)) beds.add(pos.immutable());
                        if (beds.size() >= limit) return new BedScan(List.copyOf(beds), complete);
                    }
                }
            }
        }
        return new BedScan(List.copyOf(beds), complete);
    }

    private static boolean validBed(ServerLevel level, BlockPos pos) {
        if (!loaded(level, pos)) return false;
        BlockState head = level.getBlockState(pos);
        if (!(head.getBlock() instanceof BedBlock) || head.getValue(BedBlock.PART) != BedPart.HEAD
                || !BuiltInRegistries.BLOCK.getKey(head.getBlock()).getNamespace().equals("minecraft")) return false;
        BlockPos footPos = pos.relative(head.getValue(BedBlock.FACING).getOpposite());
        if (!loaded(level, footPos)) return false;
        BlockState foot = level.getBlockState(footPos);
        return foot.is(head.getBlock()) && foot.getValue(BedBlock.PART) == BedPart.FOOT
                && foot.getValue(BedBlock.FACING) == head.getValue(BedBlock.FACING);
    }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
