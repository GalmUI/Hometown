package dev.conner.hometown.food;

import dev.conner.hometown.settlement.*;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FoodScannerTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static final BlockPos POS=new BlockPos(2,64,2);
    static final class World {
        final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache source=mock(ServerChunkCache.class);
        final Map<BlockPos,BlockEntity> entities=new HashMap<>();
        final Map<BlockPos,BlockState> states=new HashMap<>();
        final Map<String,LevelChunk> chunks=new HashMap<>();
        final Set<Integer> unavailable=new HashSet<>();
        final Set<String> unavailableChunks=new HashSet<>();
        final Settlement town=new Settlement(UUID.randomUUID(),"Stores",Level.OVERWORLD,new BlockPos(8,64,8),8,UUID.randomUUID(),"Founder",0);
        World() {
            when(level.getChunkSource()).thenReturn(source);
            when(source.getChunkNow(anyInt(),anyInt())).thenAnswer(c->{
                int x=c.getArgument(0),z=c.getArgument(1);if(unavailable.contains(x) || unavailableChunks.contains(x+":"+z))return null;
                return chunks.computeIfAbsent(x+":"+z,key->{
                    var chunk=mock(LevelChunk.class);
                    when(chunk.getBlockEntitiesPos()).thenAnswer(i->states.keySet().stream().filter(p->p.getX()>>4==x && p.getZ()>>4==z).collect(java.util.stream.Collectors.toSet()));
                    when(chunk.getBlockEntities()).thenReturn(entities);
                    when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(i->states.getOrDefault(i.getArgument(0),Blocks.AIR.defaultBlockState()));
                    return chunk;
                });
            });
        }
        BlockState tagged(BlockState original) {var state=spy(original);doReturn(true).when(state).is(FoodScanner.FOOD_STORAGE);return state;}
        BarrelBlockEntity barrel(BlockPos pos, boolean allowed) {
            var state=allowed?tagged(Blocks.BARREL.defaultBlockState()):Blocks.BARREL.defaultBlockState();
            var be=new BarrelBlockEntity(pos,state);states.put(pos,state);entities.put(pos,be);return be;
        }
        ChestBlockEntity chest(BlockPos pos, BlockState original) {
            var state=tagged(original);ChestBlockEntity be=state.is(Blocks.TRAPPED_CHEST)?new TrappedChestBlockEntity(pos,state):new ChestBlockEntity(pos,state);states.put(pos,state);entities.put(pos,be);return be;
        }
        FoodSnapshot scan(int population) {return FoodScanner.scan(level,town,new SettlementStats(population,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),8,FoodRules.DEFAULT);}
        void noLoads() {assertTrue(mockingDetails(source).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("getChunkNow")));}
    }
    @Test void emptyStoresAndNonFoodAreEmpty() {
        var w=new World();var b=w.barrel(POS,true);b.setItem(0,new ItemStack(Items.IRON_INGOT,16));b.setItem(1,new ItemStack(Items.DIAMOND_SWORD));
        var s=w.scan(10);assertEquals(0,s.totalNutrition());assertEquals(0,s.foodContainers());assertEquals(0,s.foodStacks());
        assertEquals(FoodSnapshot.FoodSecurityState.EMPTY,s.state().orElseThrow());assertEquals(0,s.reserveDays().orElseThrow());w.noLoads();
    }
    @Test void stackMathAndAddRemoveFoodNeverConsumeItems() {
        var w=new World();var b=w.barrel(POS,true);b.setItem(0,new ItemStack(Items.BREAD,20));
        long expected=(long)b.getItem(0).get(DataComponents.FOOD).nutrition()*20;
        var s=w.scan(10);assertEquals(100,expected);assertEquals(expected,s.totalNutrition());assertEquals(1,s.foodContainers());
        assertEquals(20,b.getItem(0).getCount());assertEquals(s,w.scan(10));
        b.setItem(0,ItemStack.EMPTY);assertEquals(0,w.scan(10).totalNutrition());
    }
    @Test void uniqueTypesAndDuplicateStacksAcrossStores() {
        var w=new World();var a=w.barrel(POS,true);var b=w.barrel(POS.east(),true);
        a.setItem(0,new ItemStack(Items.BREAD,64));a.setItem(1,new ItemStack(Items.CARROT,32));a.setItem(2,new ItemStack(Items.APPLE,4));
        b.setItem(0,new ItemStack(Items.BREAD,3));
        var s=w.scan(10);assertEquals(3,s.uniqueFoodTypes());assertEquals(4,s.foodStacks());assertEquals(2,s.foodContainers());
        assertEquals(67*5+32*3+4*4,s.totalNutrition());
    }
    @Test void standardFoodComponentWorksWithoutFoodItemList() {
        var w=new World();var b=w.barrel(POS,true);var synthetic=new ItemStack(Items.STICK,20);
        synthetic.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(7).saturationModifier(0).build());
        b.setItem(0,synthetic);assertEquals(140,w.scan(10).totalNutrition());
    }
    @Test void excludedAndZeroNutritionFoodsDoNotCount() {
        var w=new World();var b=w.barrel(POS,true);var excluded=spy(new ItemStack(Items.BREAD,20));
        doReturn(true).when(excluded).is(FoodScanner.FOOD_EXCLUDED);b.setItem(0,excluded);
        var zero=new ItemStack(Items.STICK);zero.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(0).build());b.setItem(1,zero);
        assertEquals(0,w.scan(10).totalNutrition());assertEquals(0,w.scan(10).foodStacks());
    }
    @Test void onlyTaggedStorageCountsAndMovingFoodChangesReserves() {
        var w=new World();var tagged=w.barrel(POS,true);var ignored=w.barrel(POS.east(),false);
        ignored.setItem(0,new ItemStack(Items.BREAD,20));assertEquals(0,w.scan(10).totalNutrition());
        tagged.setItem(0,ignored.removeItemNoUpdate(0));assertEquals(100,w.scan(10).totalNutrition());
        var hopper=new HopperBlockEntity(POS.south(),Blocks.HOPPER.defaultBlockState());hopper.setItem(0,new ItemStack(Items.BREAD,64));
        w.states.put(POS.south(),Blocks.HOPPER.defaultBlockState());w.entities.put(POS.south(),hopper);
        assertEquals(100,w.scan(10).totalNutrition());
    }
    @Test void chestTrappedChestAndBarrelSupported() {
        var w=new World();w.chest(POS,Blocks.CHEST.defaultBlockState()).setItem(0,new ItemStack(Items.BREAD,1));
        w.chest(POS.east(),Blocks.TRAPPED_CHEST.defaultBlockState()).setItem(0,new ItemStack(Items.BREAD,2));
        w.barrel(POS.south(),true).setItem(0,new ItemStack(Items.BREAD,3));
        var s=w.scan(10);assertEquals(30,s.totalNutrition());assertEquals(3,s.foodContainers());
    }
    @Test void doubleChestCountsEachLocalSlotOnceAndOneInventory() {
        for(var block:List.of(Blocks.CHEST,Blocks.TRAPPED_CHEST)) {
            var w=new World();var left=block.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.LEFT).setValue(ChestBlock.FACING,Direction.NORTH);
            BlockPos partner=POS.relative(ChestBlock.getConnectedDirection(left));
            var a=w.chest(POS,left);var b=w.chest(partner,left.setValue(ChestBlock.TYPE,ChestType.RIGHT));
            a.setItem(0,new ItemStack(Items.BREAD,20));b.setItem(0,new ItemStack(Items.BREAD,30));
            var s=w.scan(10);assertEquals(250,s.totalNutrition());assertEquals(2,s.foodStacks());assertEquals(1,s.foodContainers());
            assertEquals(20,a.getItem(0).getCount());assertEquals(30,b.getItem(0).getCount());
            a.setItem(0,ItemStack.EMPTY);assertEquals(1,w.scan(10).foodContainers());assertEquals(150,w.scan(10).totalNutrition());
        }
    }
    @Test void doubleChestBoundaryCountsOnlyInsideHalf() {
        var w=new World();BlockPos inside=new BlockPos(0,64,2);
        var left=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.LEFT).setValue(ChestBlock.FACING,Direction.SOUTH);
        BlockPos outside=inside.relative(ChestBlock.getConnectedDirection(left));assertEquals(-1,outside.getX());
        w.chest(inside,left).setItem(0,new ItemStack(Items.BREAD,2));
        w.chest(outside,left.setValue(ChestBlock.TYPE,ChestType.RIGHT)).setItem(0,new ItemStack(Items.BREAD,64));
        var s=w.scan(10);assertEquals(10,s.totalNutrition());assertEquals(1,s.foodContainers());w.noLoads();
    }
    @Test void horizontalAndVerticalBoundsExcludeStores() {
        var w=new World();w.barrel(new BlockPos(2,80,2),true).setItem(0,new ItemStack(Items.BREAD,64));
        w.barrel(new BlockPos(18,64,2),true).setItem(0,new ItemStack(Items.BREAD,64));
        assertEquals(0,w.scan(10).totalNutrition());
    }
    @Test void missingChunkAndPendingStorageAreIncomplete() {
        var w=new World();w.unavailable.add(1);assertEquals(FoodScanStatus.PARTIAL,w.scan(10).scanStatus());w.noLoads();
        w=new World();w.states.put(POS,w.tagged(Blocks.BARREL.defaultBlockState()));
        var s=w.scan(10);assertFalse(s.scanComplete());assertTrue(s.reserveDays().isEmpty());
    }
    @Test void ungeneratedLootIsNeverUnpacked() {
        var w=new World();var chest=spy(w.chest(POS,Blocks.CHEST.defaultBlockState()));w.entities.put(POS,chest);
        chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath("minecraft","chests/simple_dungeon")));
        assertFalse(w.scan(10).scanComplete());verify(chest,never()).getItem(anyInt());
        assertNotNull(chest.getLootTable());
    }
    @Test void slotLimitAndUnavailableInventoryFailCleanly() {
        var w=new World();var barrel=spy(w.barrel(POS,true));w.entities.put(POS,barrel);
        doReturn(FoodScanner.MAX_SLOTS+1).when(barrel).getContainerSize();assertFalse(w.scan(10).scanComplete());assertTrue(w.scan(10).diagnostics().slotsInspected()<=FoodScanner.MAX_SLOTS);
        doReturn(27).when(barrel).getContainerSize();doThrow(new IllegalStateException("unavailable storage")).when(barrel).getItem(0);
        assertFalse(w.scan(10).scanComplete());
    }
    @Test void nutritionUsesLongAndOverflowIsIncomplete() {
        var w=new World();var barrel=spy(w.barrel(POS,true));w.entities.put(POS,barrel);
        var large=new ItemStack(Items.BREAD);large.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(Integer.MAX_VALUE).build());large.setCount(Integer.MAX_VALUE);
        doReturn(large).when(barrel).getItem(0);var s=w.scan(10);assertEquals((long)Integer.MAX_VALUE*Integer.MAX_VALUE,s.totalNutrition());
        doReturn(large).when(barrel).getItem(1);doReturn(large).when(barrel).getItem(2);assertFalse(w.scan(10).scanComplete());
    }
    @Test void thresholdsPopulationAndVisualCap() {
        var rules=FoodRules.DEFAULT;
        double[] days={0,0.5,1,2,3,5,7,8,12};
        var states=new FoodSnapshot.FoodSecurityState[]{FoodSnapshot.FoodSecurityState.EMPTY,FoodSnapshot.FoodSecurityState.CRITICAL,FoodSnapshot.FoodSecurityState.LOW,FoodSnapshot.FoodSecurityState.LOW,FoodSnapshot.FoodSecurityState.STABLE,FoodSnapshot.FoodSecurityState.STABLE,FoodSnapshot.FoodSecurityState.STOCKED,FoodSnapshot.FoodSecurityState.STOCKED,FoodSnapshot.FoodSecurityState.STOCKED};
        for(int i=0;i<days.length;i++){var s=rules.snapshot(10,1,1,1,(long)(days[i]*200));assertEquals(states[i],s.state().orElseThrow());assertEquals(days[i],s.reserveDays().orElseThrow());}
        assertEquals(50,rules.snapshot(10,1,1,1,700).barPercent().orElseThrow());
        var twelve=rules.snapshot(10,1,1,1,2400);assertEquals(12,twelve.reserveDays().orElseThrow());assertEquals(100,twelve.barPercent().orElseThrow());
        assertEquals(5.3,rules.snapshot(10,1,1,1,1060).reserveDays().orElseThrow());assertEquals(2.65,rules.snapshot(20,1,1,1,1060).reserveDays().orElseThrow());
        assertEquals(10.6,rules.snapshot(5,1,1,1,1060).reserveDays().orElseThrow());
    }
    @Test void zeroPopulationAndCustomDailyNeed() {
        var empty=FoodRules.DEFAULT.snapshot(0,1,1,1,1060);assertEquals(FoodSnapshot.FoodSecurityState.NO_RESIDENTS,empty.state().orElseThrow());assertTrue(empty.reserveDays().isEmpty());assertTrue(empty.barPercent().isEmpty());
        var custom=new FoodRules(10,1,3,7).snapshot(10,1,1,1,1060);assertEquals(100,custom.dailyNutritionRequirement());assertEquals(10.6,custom.reserveDays().orElseThrow());
    }
    @Test void duplicateContainerIdentityIsReadOnce() {
        var w=new World();var barrel=w.barrel(POS,true);barrel.setItem(0,new ItemStack(Items.BREAD,20));
        w.states.put(POS.east(),w.tagged(Blocks.BARREL.defaultBlockState()));w.entities.put(POS.east(),barrel);
        var s=w.scan(10);assertEquals(100,s.totalNutrition());assertEquals(1,s.foodStacks());assertEquals(1,s.foodContainers());
    }
    @Test void storageAndExclusionResourcesHaveExpectedDefaults() throws Exception {
        try(var input=getClass().getResourceAsStream("/data/hometown/tags/block/food_storage.json")) {
            assertNotNull(input);var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
            var values=json.getAsJsonArray("values");assertEquals(3,values.size());
            assertEquals(Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:barrel"),
                    java.util.stream.StreamSupport.stream(values.spliterator(),false).map(e->e.getAsString()).collect(java.util.stream.Collectors.toSet()));
        }
        try(var input=getClass().getResourceAsStream("/data/hometown/tags/item/food_excluded.json")) {
            assertNotNull(input);assertEquals(0,com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject().getAsJsonArray("values").size());
        }
    }
    @Test void foodPacketRoundTripPreservesHousingAndLargeValues() {
        var w=new World();var stats=new SettlementStats(10,0,0,0,SettlementStats.Availability.COMPLETE,List.of());
        for(var food:List.of(FoodRules.DEFAULT.snapshot(10,1,1,1,1060),FoodRules.DEFAULT.snapshot(10,1,1,1,5000000000L),FoodRules.DEFAULT.snapshot(0,0,0,0,0),FoodSnapshot.unavailable(10))) {
            var base=TownLedgerSnapshot.of(w.town,stats,TownLedgerSnapshot.BellState.PRESENT,0);var snapshot=base.withFood(food);
            assertEquals(base.housing(),snapshot.housing());assertEquals(food,snapshot.withHousing(base.housing()).food());
            var packet=new TownLedgerSnapshotPayload(1,snapshot,TownLedgerSnapshotPayload.Error.NONE);var b=new FriendlyByteBuf(Unpooled.buffer());
            try{TownLedgerSnapshotPayload.STREAM_CODEC.encode(b,packet);assertEquals(packet,TownLedgerSnapshotPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        }
    }
}
