package dev.conner.hometown.food;

import dev.conner.hometown.observation.*;
import dev.conner.hometown.settlement.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoodGrowingCollectorTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static final ObservationMetadata META=new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",1,0,0,0,0);
    private CropRules.Definitions definitions(){
        var rules=new HashMap<ResourceLocation,CropRules.Rule>();
        add(rules,Blocks.WHEAT,"hometown:wheat",Map.of("age","7"));add(rules,Blocks.CARROTS,"hometown:carrot",Map.of("age","7"));
        add(rules,Blocks.POTATOES,"hometown:potato",Map.of("age","7"));add(rules,Blocks.BEETROOTS,"hometown:beetroot",Map.of("age","3"));
        return new CropRules.Definitions(rules);
    }
    private void add(Map<ResourceLocation,CropRules.Rule> rules,net.minecraft.world.level.block.Block block,String family,Map<String,String> mature){
        var id=BuiltInRegistries.BLOCK.getKey(block);rules.put(id,new CropRules.Rule(id,ResourceLocation.parse(family),Map.of(),Optional.of(mature)));
    }
    private record Fixture(FoodGrowingSnapshot snapshot,ServerChunkCache chunks){}
    private Fixture collect(Map<BlockPos,BlockState> states,int limit){
        var level=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);var section=mock(LevelChunkSection.class);
        when(level.getChunkSource()).thenReturn(chunks);when(level.getMinBuildHeight()).thenReturn(0);when(level.getMaxBuildHeight()).thenReturn(128);when(level.getSectionYFromSectionIndex(0)).thenReturn(4);
        when(chunks.getChunkNow(0,0)).thenReturn(chunk);when(chunk.getSections()).thenReturn(new LevelChunkSection[]{section});when(section.hasOnlyAir()).thenReturn(false);
        when(section.maybeHas(any())).thenAnswer(call->{@SuppressWarnings("unchecked") Predicate<BlockState> p=call.getArgument(0);return p.test(Blocks.WHEAT.defaultBlockState())||p.test(Blocks.CARROTS.defaultBlockState())||p.test(Blocks.POTATOES.defaultBlockState());});
        when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(call->states.getOrDefault(((BlockPos)call.getArgument(0)).immutable(),Blocks.AIR.defaultBlockState()));
        var town=new Settlement(UUID.randomUUID(),"Growtest",Level.OVERWORLD,new BlockPos(8,64,8),4,UUID.randomUUID(),"Founder",0);
        var cache=new BlockObservationCache(level,SettlementQueries.bounds(town.bellPosition(),town.radius(),1),limit);
        return new Fixture(FoodGrowingCollector.collect(level,town,1,META,true,definitions(),cache),chunks);
    }
    private List<BlockPos> positions(int count){
        // Mock section index 0 is section Y=4 (blocks 64-79), while the town's vertical test scope is 63-65.
        // Keep fixture crops at Y=64-65 so they are both inside the scope and actually represented by that section.
        var out=new ArrayList<BlockPos>();outer:for(int x=4;x<=12;x++)for(int y=64;y<=65;y++)for(int z=4;z<=12;z++){out.add(new BlockPos(x,y,z));if(out.size()==count)break outer;}return out;
    }
    @Test void F06_smallFixtureEightGrowingThreeMatureTwoFamilies(){
        var p=positions(8);var map=new HashMap<BlockPos,BlockState>();
        for(int i=0;i<5;i++)map.put(p.get(i),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,i<2?7:3));
        for(int i=5;i<8;i++)map.put(p.get(i),Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7,i==5?7:2));
        var fixture=collect(map,10000);var s=fixture.snapshot();assertEquals(FoodGrowingSnapshot.Status.COMPLETE,s.scanStatus());
        assertEquals(8,s.growingCropBlocks(),"all eight fixture crops must be discovered");
        assertEquals(3,s.knownMatureCropBlocks(),"two mature wheat plus one mature carrot");
        assertEquals(2,s.cropFamilyCount(),"wheat and carrot families");
        assertEquals(0,s.maturityUnassessedCropBlocks());
        assertTrue(mockingDetails(fixture.chunks()).getInvocations().stream().allMatch(inv->inv.getMethod().getName().equals("getChunkNow")),"Growing may use only loaded-only getChunkNow access");
    }
    @Test void F06_largeFixtureFiftySixGrowingTwentyTwoMatureThreeFamilies(){
        var p=positions(56);var map=new HashMap<BlockPos,BlockState>();
        for(int i=0;i<56;i++){
            BlockState state;
            if(i<24)state=Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,i<10?7:4);
            else if(i<44)state=Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7,i<28?7:3);
            else state=Blocks.POTATOES.defaultBlockState().setValue(BlockStateProperties.AGE_7,i<48?7:2);
            map.put(p.get(i),state);
        }
        // Normalize exactly 22 mature regardless of family split above.
        int mature=0;for(var pos:p){var state=map.get(pos);if(state.hasProperty(BlockStateProperties.AGE_7)&&state.getValue(BlockStateProperties.AGE_7)==7)mature++;}
        if(mature!=22){int delta=22-mature;for(var pos:p){if(delta==0)break;var state=map.get(pos);int age=state.getValue(BlockStateProperties.AGE_7);if(delta>0&&age!=7){map.put(pos,state.setValue(BlockStateProperties.AGE_7,7));delta--;}else if(delta<0&&age==7){map.put(pos,state.setValue(BlockStateProperties.AGE_7,1));delta++;}}}
        var s=collect(map,10000).snapshot();
        assertEquals(56,s.growingCropBlocks(),"all fixture crops must be discovered");
        assertEquals(22,s.knownMatureCropBlocks(),"fixture maturity total must remain exact");
        assertEquals(3,s.cropFamilyCount(),"wheat, carrot, and potato families");
    }
    @Test void F08_customOmittedMaturityCountsGrowingButNeverInventsZero(){
        var id=BuiltInRegistries.BLOCK.getKey(Blocks.SWEET_BERRY_BUSH);var defs=new CropRules.Definitions(Map.of(id,new CropRules.Rule(id,ResourceLocation.parse("example:berries"),Map.of("age","2"),Optional.empty())));
        var level=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);var section=mock(LevelChunkSection.class);
        when(level.getChunkSource()).thenReturn(chunks);when(level.getMinBuildHeight()).thenReturn(0);when(level.getMaxBuildHeight()).thenReturn(128);when(level.getSectionYFromSectionIndex(0)).thenReturn(4);when(chunks.getChunkNow(0,0)).thenReturn(chunk);when(chunk.getSections()).thenReturn(new LevelChunkSection[]{section});when(section.hasOnlyAir()).thenReturn(false);
        when(section.maybeHas(any())).thenAnswer(c->{@SuppressWarnings("unchecked")Predicate<BlockState> p=c.getArgument(0);return p.test(Blocks.SWEET_BERRY_BUSH.defaultBlockState());});
        var target=new BlockPos(8,64,8);when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(c->target.equals(c.getArgument(0))?Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3,2):Blocks.AIR.defaultBlockState());
        var town=new Settlement(UUID.randomUUID(),"Berries",Level.OVERWORLD,target,1,UUID.randomUUID(),"Founder",0);var cache=new BlockObservationCache(level,SettlementQueries.bounds(target,1,1),1000);
        var s=FoodGrowingCollector.collect(level,town,1,META,true,defs,cache);assertEquals(FoodGrowingSnapshot.Status.COMPLETE,s.scanStatus());assertEquals(1,s.growingCropBlocks());assertEquals(1,s.maturityUnassessedCropBlocks());
        assertEquals(FoodGrowingSnapshot.MaturityStatus.UNASSESSED,s.families().getFirst().maturityStatus());assertTrue(s.families().getFirst().mature().isEmpty());
    }
    @Test void Q_budgetStopsCleanlyAndRetainsNoInventedCompleteness(){
        var map=Map.of(new BlockPos(8,64,8),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7));var s=collect(map,1).snapshot();
        assertNotEquals(FoodGrowingSnapshot.Status.COMPLETE,s.scanStatus());assertTrue(s.reasonCounts().containsKey(FoodGrowingSnapshot.Reason.SCAN_LIMIT_REACHED));
    }
}
