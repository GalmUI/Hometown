package dev.conner.hometown.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/** Read-only access through getChunkNow, including neighbor reads requested by collision shapes. */
public final class LoadedRoomWorld implements RoomDetector.WorldView, BlockGetter {
    public static final TagKey<Block> ROOM_BOUNDARIES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("hometown", "room_boundaries"));
    private final ServerLevel level;
    private boolean unavailableNeighbor;
    public LoadedRoomWorld(ServerLevel level) { this.level = level; }
    private LevelChunk chunk(BlockPos p) { return level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4); }
    @Override public BlockState getBlockState(BlockPos p) {
        LevelChunk chunk = chunk(p);
        if (chunk == null) { unavailableNeighbor = true; return Blocks.BEDROCK.defaultBlockState(); }
        return chunk.getBlockState(p);
    }
    @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos p) {
        LevelChunk chunk = chunk(p);
        if (chunk == null) { unavailableNeighbor = true; return null; }
        // Reading existing entities only: do not instantiate them for shape queries.
        return chunk.getBlockEntities().get(p);
    }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }

    public BlockPos intactBedAt(BlockPos p) {
        LevelChunk chunk = chunk(p);
        if (chunk == null) return null;
        unavailableNeighbor = false;
        BlockPos head = bedHead(p, chunk.getBlockState(p));
        return unavailableNeighbor ? null : head;
    }

    @Override public RoomDetector.Cell cell(BlockPos p) {
        LevelChunk chunk = chunk(p);
        if (chunk == null) return RoomDetector.Cell.UNAVAILABLE;
        if (p.getY() < getMinBuildHeight() || p.getY() >= getMinBuildHeight() + getHeight())
            return new RoomDetector.Cell(true, false, true, null);
        unavailableNeighbor = false;
        BlockState state = chunk.getBlockState(p);
        BlockPos bed = bedHead(p, state);
        boolean boundary = bed == null && (state.is(ROOM_BOUNDARIES)
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || !state.getCollisionShape(this, p).isEmpty());
        if (unavailableNeighbor) return RoomDetector.Cell.UNAVAILABLE;
        // WORLD_SURFACE includes glass/leaves: sky light alone would misclassify glass roofs.
        // This is only an early escape proof. Roofed outdoor/cave paths still use bounded flood fill.
        boolean outside = !boundary && p.getY() > chunk.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX() & 15, p.getZ() & 15);
        return new RoomDetector.Cell(true, boundary, outside, bed);
    }

    private BlockPos bedHead(BlockPos p, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return null;
        boolean head = state.getValue(BedBlock.PART) == BedPart.HEAD;
        BlockPos other = p.relative(head ? state.getValue(BedBlock.FACING).getOpposite() : state.getValue(BedBlock.FACING));
        BlockState otherState = getBlockState(other);
        if (!otherState.is(state.getBlock()) || otherState.getValue(BedBlock.PART) == state.getValue(BedBlock.PART)
                || otherState.getValue(BedBlock.FACING) != state.getValue(BedBlock.FACING)) return null;
        return head ? p.immutable() : other.immutable();
    }
}
