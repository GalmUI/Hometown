package dev.conner.hometown.room;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomDetectorTest {
    private static final BlockPos BED = new BlockPos(2, 1, 2);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    /** Real block states and collision shapes, backed exclusively by mocked already-loaded chunks. */
    static final class Fixture {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final ServerLevel level = mock(ServerLevel.class);
        final ServerChunkCache source = mock(ServerChunkCache.class);
        final Map<String, LevelChunk> chunks = new HashMap<>();
        final Set<Integer> missingChunkX = new HashSet<>();
        final LoadedRoomWorld world;
        Fixture() {
            when(level.getHeight()).thenReturn(384);
            when(level.getMinBuildHeight()).thenReturn(-64);
            when(level.getChunkSource()).thenReturn(source);
            when(source.getChunkNow(anyInt(), anyInt())).thenAnswer(call -> {
                int cx = call.getArgument(0), cz = call.getArgument(1);
                if (missingChunkX.contains(cx)) return null;
                return chunks.computeIfAbsent(cx + ":" + cz, key -> {
                    LevelChunk chunk = mock(LevelChunk.class);
                    when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(c -> state(c.getArgument(0)));
                    when(chunk.getHeight(eq(Heightmap.Types.WORLD_SURFACE), anyInt(), anyInt())).thenAnswer(c -> {
                        int x = cx * 16 + (int)c.getArgument(1), z = cz * 16 + (int)c.getArgument(2);
                        return Math.max(0, blocks.entrySet().stream().filter(e -> e.getKey().getX() == x && e.getKey().getZ() == z
                                && !e.getValue().isAir()).mapToInt(e -> e.getKey().getY()).max().orElse(0));
                    });
                    return chunk;
                });
            });
            world = new LoadedRoomWorld(level);
        }
        BlockState state(BlockPos p) { return blocks.getOrDefault(p, (p.getY() <= 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState()); }
        void put(int x, int y, int z, BlockState state) { blocks.put(new BlockPos(x,y,z), state); }
        void room(int sizeX, int sizeZ, int roof, BlockState material) {
            for (BlockPos p : BlockPos.betweenClosed(0,0,0,sizeX,roof,sizeZ))
                if (p.getX()==0 || p.getX()==sizeX || p.getZ()==0 || p.getZ()==sizeZ || p.getY()==0 || p.getY()==roof)
                    blocks.put(p.immutable(), material);
            bed(BED);
        }
        void bed(BlockPos head) {
            blocks.put(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH));
            blocks.put(head.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH));
        }
        RoomDetectionResult detect() { return new RoomDetector(world).detect(BED); }
        void assertNoLoads() {
            assertTrue(mockingDetails(source).getInvocations().stream().allMatch(i -> i.getMethod().getName().equals("getChunkNow")));
            assertTrue(mockingDetails(level).getInvocations().stream().allMatch(i -> Set.of("getChunkSource","getHeight","getMinBuildHeight").contains(i.getMethod().getName())));
        }
    }
    private Fixture normal() { Fixture f = new Fixture(); f.room(6,6,4,Blocks.OAK_PLANKS.defaultBlockState()); return f; }
    private void enclosed(RoomDetectionResult r, int beds) {
        assertTrue(r.enclosed(), r.toString()); assertEquals(RoomFailureReason.NONE, r.failureReason()); assertEquals(beds,r.bedCount());
    }
    @Test void vanillaHouseAndBothBedHalves() {
        Fixture f=normal(); RoomDetector d=new RoomDetector(f.world); var r=d.detect(BED);
        enclosed(r,1); assertEquals(75,r.interiorVolume()); assertSame(r,d.detect(BED.south())); f.assertNoLoads();
    }
    @Test void openAndClosedDoorsKeepIdenticalResult() {
        Fixture f=normal(); RoomDetectionResult previous=null;
        for (boolean open : new boolean[]{false,true}) {
            for (int y=1;y<=2;y++) f.put(0,y,2,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,open)
                    .setValue(DoorBlock.HALF,y==1?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER));
            var r=f.detect(); enclosed(r,1); if(previous!=null) assertEquals(previous,r); previous=r;
        }
    }
    @Test void missingRoofEscapesOutside() {
        Fixture f=normal(); f.put(2,4,2,Blocks.AIR.defaultBlockState());
        assertEquals(RoomFailureReason.ESCAPED_TO_OUTSIDE,f.detect().failureReason());
    }
    @Test void awningAndTreeDoNotEnclose() {
        for (BlockState roof : List.of(Blocks.OAK_PLANKS.defaultBlockState(),Blocks.OAK_LEAVES.defaultBlockState())) {
            Fixture f=new Fixture(); f.bed(BED);
            for (int x=0;x<=6;x++) for(int z=0;z<=6;z++) f.put(x,4,z,roof);
            assertEquals(RoomFailureReason.ESCAPED_TO_OUTSIDE,f.detect().failureReason());
        }
    }
    @Test void enclosedCarvedCave() {
        Fixture f=new Fixture();
        for(BlockPos p:BlockPos.betweenClosed(1,-20,1,5,-18,5)) f.blocks.put(p.immutable(),Blocks.AIR.defaultBlockState());
        BlockPos underground=new BlockPos(2,-20,2); f.bed(underground);
        var r=new RoomDetector(f.world).detect(underground); enclosed(r,1); assertEquals(75,r.interiorVolume());
    }
    @Test void glassRoofAndGlassOrPaneWalls() {
        for(BlockState wall:List.of(Blocks.GLASS.defaultBlockState(),Blocks.GLASS_PANE.defaultBlockState(),Blocks.COBBLESTONE_WALL.defaultBlockState())) {
            Fixture f=new Fixture(); f.room(6,6,4,wall); enclosed(f.detect(),1);
        }
    }
    @Test void twoBedsReuseCompleteRoomWithoutWorldReads() {
        Fixture f=normal(); BlockPos second=new BlockPos(4,1,2); f.bed(second);
        RoomDetector d=new RoomDetector(f.world); var r=d.detect(BED); enclosed(r,2);
        int reads=d.inspectedCells(); assertSame(r,d.detect(second)); assertEquals(reads,d.inspectedCells());
        assertThrows(UnsupportedOperationException.class,()->r.bedPositions().clear());
        assertEquals(r,new RoomDetector(f.world).detect(second));
    }
    @Test void fourBedsShareOneRoom() {
        Fixture f=new Fixture(); f.room(10,10,4,Blocks.STONE.defaultBlockState());
        for(int x=4;x<=8;x+=2) f.bed(new BlockPos(x,1,2));
        enclosed(f.detect(),4);
    }
    @Test void separateBedroomsWithOpenDoorStaySeparate() {
        Fixture f=new Fixture(); f.room(8,6,4,Blocks.STONE.defaultBlockState()); BlockPos second=new BlockPos(6,1,2); f.bed(second);
        for(int y=1;y<4;y++) for(int z=1;z<6;z++) f.put(4,y,z,Blocks.STONE.defaultBlockState());
        for(int y=1;y<=2;y++) f.put(4,y,2,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,true)
                .setValue(DoorBlock.HALF,y==1?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER));
        RoomDetector d=new RoomDetector(f.world); var a=d.detect(BED); var b=d.detect(second);
        enclosed(a,1); enclosed(b,1); assertNotEquals(a.representativePosition(),b.representativePosition());
    }
    @Test void largeOpenPlanInterior() {
        Fixture f=new Fixture(); f.room(20,16,6,Blocks.STONE.defaultBlockState());
        var r=f.detect(); enclosed(r,1); assertEquals(19*15*5,r.interiorVolume());
    }
    @Test void oversizedHallStopsAtVolumeLimit() {
        Fixture f=new Fixture(); f.room(28,28,10,Blocks.STONE.defaultBlockState());
        RoomDetector d=new RoomDetector(f.world); var r=d.detect(BED);
        assertEquals(RoomFailureReason.MAX_VOLUME_EXCEEDED,r.failureReason()); assertEquals(4096,r.interiorVolume());
        assertTrue(d.inspectedCells()<=RoomDetector.Limits.DEFAULT.maximumInspectedCells()); f.assertNoLoads();
    }
    @Test void brokenWallEscapesOutside() {
        Fixture f=normal(); f.put(0,1,2,Blocks.AIR.defaultBlockState()); f.put(0,2,2,Blocks.AIR.defaultBlockState());
        assertEquals(RoomFailureReason.ESCAPED_TO_OUTSIDE,f.detect().failureReason());
    }
    @Test void openAndClosedTrapdoorRoofStable() {
        Fixture f=normal(); RoomDetectionResult previous=null;
        for(boolean open:new boolean[]{false,true}) {
            f.put(2,4,2,Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,open));
            var r=f.detect(); enclosed(r,1); if(previous!=null) assertEquals(previous,r); previous=r;
        }
    }
    @Test void slabAndStairRoofsSealAtBlockCellResolution() {
        for(BlockState roof:List.of(Blocks.OAK_SLAB.defaultBlockState(),Blocks.OAK_STAIRS.defaultBlockState())) {
            Fixture f=normal(); for(int x=0;x<=6;x++) for(int z=0;z<=6;z++) f.put(x,4,z,roof);
            enclosed(f.detect(),1);
        }
    }
    @Test void missingChunkFailsWithoutRequestingLoad() {
        Fixture f=new Fixture(); f.room(20,6,4,Blocks.STONE.defaultBlockState()); f.missingChunkX.add(1);
        assertEquals(RoomFailureReason.CHUNK_UNAVAILABLE,f.detect().failureReason()); f.assertNoLoads();
    }
    @Test void distanceAndWorkBudgetsAreExplicit() {
        Fixture f=normal();
        assertEquals(RoomFailureReason.MAX_DISTANCE_EXCEEDED,new RoomDetector(f.world,new RoomDetector.Limits(4096,1,16,65536)).detect(BED).failureReason());
        Fixture tall=new Fixture(); tall.room(6,6,6,Blocks.STONE.defaultBlockState());
        assertEquals(RoomFailureReason.MAX_DISTANCE_EXCEEDED,new RoomDetector(tall.world,new RoomDetector.Limits(4096,32,1,65536)).detect(BED).failureReason());
        RoomDetector d=new RoomDetector(f.world,new RoomDetector.Limits(4096,32,16,10));
        assertEquals(RoomFailureReason.SCAN_BUDGET_EXCEEDED,d.detect(BED).failureReason()); assertEquals(10,d.inspectedCells());
    }
    @Test void invalidBedAndNoFreeStart() {
        Fixture f=normal(); f.put(2,1,3,Blocks.AIR.defaultBlockState());
        assertEquals(RoomFailureReason.NO_VALID_INTERIOR_START,f.detect().failureReason());
        f=normal(); for(BlockPos p:BlockPos.betweenClosed(1,1,1,3,2,4))
            if(!p.equals(BED) && !p.equals(BED.south())) f.blocks.put(p.immutable(),Blocks.STONE.defaultBlockState());
        assertEquals(RoomFailureReason.NO_VALID_INTERIOR_START,f.detect().failureReason());
    }
    @Test void tagCanSealAnOtherwiseNonCollidingBlock() {
        Fixture f=normal(); BlockState separator=mock(BlockState.class);
        when(separator.getBlock()).thenReturn(Blocks.STRUCTURE_VOID); when(separator.is(LoadedRoomWorld.ROOM_BOUNDARIES)).thenReturn(true);
        f.put(0,1,2,separator); enclosed(f.detect(),1);
        verify(separator,never()).getCollisionShape(any(),any());
    }
    @Test void freshSessionRediscoverChanges() {
        Fixture f=normal(); enclosed(f.detect(),1); f.put(2,4,2,Blocks.AIR.defaultBlockState());
        assertEquals(RoomFailureReason.ESCAPED_TO_OUTSIDE,f.detect().failureReason());
    }
    @Test void infiniteRoofedTunnelFailsEvenWithoutSkyVisibility() {
        RoomDetector.WorldView tunnel=p->new RoomDetector.Cell(true,p.getY()!=0 || p.getZ()!=0,false,
                p.equals(BlockPos.ZERO)?BlockPos.ZERO:null);
        RoomDetector d=new RoomDetector(tunnel,new RoomDetector.Limits(4096,8,8,1000));
        assertEquals(RoomFailureReason.MAX_DISTANCE_EXCEEDED,d.detect(BlockPos.ZERO).failureReason());
        assertTrue(d.inspectedCells()<200);
    }
}
