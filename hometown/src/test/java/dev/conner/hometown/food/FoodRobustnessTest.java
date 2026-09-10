package dev.conner.hometown.food;

import dev.conner.hometown.settlement.*;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodRobustnessTest {
    private static final BlockPos POS=new BlockPos(2,64,2);
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private FoodScannerTest.World stocked(){var w=new FoodScannerTest.World();w.barrel(POS,true).setItem(0,new ItemStack(Items.BREAD,20));return w;}
    private FoodSnapshot limited(FoodScannerTest.World w,FoodScanner.Limits limits){return FoodScanner.scan(w.level,w.town,new SettlementStats(10,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),8,FoodRules.DEFAULT,limits);}
    @Test void completeHasExactChunkAndInventoryCounters(){
        var w=stocked();var f=w.scan(10);assertEquals(FoodScanStatus.COMPLETE,f.scanStatus());assertEquals(FoodScanReason.NONE,f.diagnostics().primaryReason());
        assertEquals(4,f.diagnostics().settlementChunks());assertEquals(4,f.diagnostics().loadedChunks());assertEquals(0,f.diagnostics().unavailableChunks());
        assertEquals(1,f.diagnostics().blockEntitiesInspected());assertEquals(1,f.diagnostics().storageFound());assertEquals(1,f.diagnostics().storageScanned());
        assertEquals(27,f.diagnostics().slotsInspected());assertEquals(100,f.totalNutrition());w.noLoads();
    }
    @Test void oneUnloadedFringeKeepsKnownFood(){
        var w=stocked();w.unavailableChunks.add("1:1");var f=w.scan(10);
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertEquals(1,f.foodContainers());
        assertEquals(.5,f.reserveDays().orElseThrow());assertTrue(f.state().isEmpty());assertEquals(FoodScanReason.UNLOADED_CHUNKS,f.diagnostics().primaryReason());
        assertEquals(3,f.diagnostics().loadedChunks());assertEquals(1,f.diagnostics().unavailableChunks());w.noLoads();
    }
    @Test void severalUnloadedChunksExcludeTheirFoodWithoutResettingKnownTotals(){
        var w=stocked();w.barrel(new BlockPos(16,64,2),true).setItem(0,new ItemStack(Items.BREAD,64));w.unavailable.add(1);
        var f=w.scan(10);assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertEquals(2,f.diagnostics().unavailableChunks());w.noLoads();
    }
    @Test void noKnownFoodPartialNeverReportsEmpty(){
        var w=new FoodScannerTest.World();w.unavailableChunks.add("1:1");var f=w.scan(10);
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(0,f.totalNutrition());assertEquals(0,f.reserveDays().orElseThrow());assertTrue(f.state().isEmpty());
        w.unavailableChunks.clear();assertEquals(FoodSnapshot.FoodSecurityState.EMPTY,w.scan(10).state().orElseThrow());
    }
    @Test void allChunksUnavailableHasNoReserveEstimate(){
        var w=stocked();w.unavailable.addAll(Set.of(0,1));var f=w.scan(10);
        assertEquals(FoodScanStatus.UNAVAILABLE,f.scanStatus());assertEquals(4,f.diagnostics().unavailableChunks());assertTrue(f.reserveDays().isEmpty());assertTrue(f.state().isEmpty());w.noLoads();
    }
    @Test void blockEntityLimitAfterDataPreservesCollectedStore(){
        var w=stocked();w.barrel(POS.east(),true).setItem(0,new ItemStack(Items.BREAD,30));
        var f=limited(w,new FoodScanner.Limits(1,4096,131072));
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertEquals(FoodScanReason.SCAN_LIMIT_REACHED,f.diagnostics().primaryReason());
        assertTrue(f.diagnostics().scanLimitHit());assertEquals(1,f.diagnostics().blockEntitiesInspected());assertEquals(1,f.diagnostics().limits().blockEntities());
    }
    @Test void limitBeforeUsefulDataIsUnavailable(){
        var w=stocked();for(var limits:List.of(new FoodScanner.Limits(0,4096,131072),new FoodScanner.Limits(32768,0,131072),new FoodScanner.Limits(32768,4096,0))){
            var f=limited(w,limits);assertEquals(FoodScanStatus.UNAVAILABLE,f.scanStatus());assertEquals(FoodScanReason.SCAN_LIMIT_REACHED,f.diagnostics().primaryReason());assertEquals(0,f.totalNutrition());
        }
    }
    @Test void slotLimitRetainsSuccessfullyReadStacksInPartiallyReadInventory(){
        var w=stocked();var f=limited(w,new FoodScanner.Limits(32768,4096,1));
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertEquals(1,f.foodStacks());assertEquals(1,f.foodContainers());
        assertEquals(0,f.diagnostics().storageScanned());assertEquals(1,f.diagnostics().slotsInspected());assertTrue(f.diagnostics().scanLimitHit());
    }
    @Test void unavailableContainerDoesNotEraseReadableContainer(){
        var w=stocked();w.states.put(POS.east(),w.tagged(Blocks.BARREL.defaultBlockState()));var f=w.scan(10);
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertEquals(FoodScanReason.STORAGE_UNAVAILABLE,f.diagnostics().primaryReason());
        assertEquals(2,f.diagnostics().storageFound());assertEquals(1,f.diagnostics().storageScanned());assertEquals(1,f.diagnostics().storageUnavailable());
    }
    @Test void exceptionAfterAReadRetainsThatReadAndContinuesOtherStores(){
        var w=stocked();var barrel=spy((net.minecraft.world.level.block.entity.BarrelBlockEntity)w.entities.get(POS));w.entities.put(POS,barrel);
        doThrow(new IllegalStateException("test inventory read failure")).when(barrel).getItem(1);
        w.barrel(POS.east(),true).setItem(0,new ItemStack(Items.BREAD,10));var f=w.scan(10);
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(150,f.totalNutrition());assertEquals(FoodScanReason.INTERNAL_ERROR,f.diagnostics().primaryReason());
        assertEquals(1,f.diagnostics().storageScanned());
    }
    @Test void incompletePopulationNoLongerPreventsStorageScan(){
        var w=stocked();w.unavailableChunks.add("1:1");
        var stats=new SettlementStats(10,0,0,0,SettlementStats.Availability.PARTIAL,List.of());
        var f=FoodScanner.scan(w.level,w.town,stats,8,FoodRules.DEFAULT);
        assertEquals(FoodScanStatus.PARTIAL,f.scanStatus());assertEquals(100,f.totalNutrition());assertTrue(f.reserveDays().isEmpty());assertTrue(f.barPercent().isEmpty());assertFalse(f.diagnostics().populationComplete());
        assertTrue(FoodDebugReport.format("Oured",f).contains("known residents only"));
        var more=FoodScanner.scan(w.level,w.town,new SettlementStats(20,0,0,0,SettlementStats.Availability.PARTIAL,List.of()),8,FoodRules.DEFAULT);
        assertTrue(more.reserveDays().isEmpty());assertEquals(100,more.totalNutrition());
    }
    @Test void diagnosticsSurviveWireAndExposeAllRequiredCounters(){
        var w=stocked();w.unavailableChunks.add("1:1");var f=limited(w,new FoodScanner.Limits(32768,4096,1));
        String report=FoodDebugReport.format("Oured",f);
        for(String text:List.of("Oured","Scan Status: PARTIAL","Reason: SCAN_LIMIT_REACHED","UNLOADED_CHUNKS","Settlement chunks considered: 4","Loaded chunks: 3","Unavailable chunks: 1",
                "Block entities inspected: 1 / 32768","Food storage containers found: 1","Food storage containers successfully scanned: 0","Duplicate inventories skipped: 0",
                "Inventory slots inspected: 1 / 1","Scan limit reached: Yes","Population: 10","Food stacks: 1","Food types: 1","Known Total Nutrition: 100","Known Reserve Days: 0.50")) assertTrue(report.contains(text),text);
        var base=TownLedgerSnapshot.of(w.town,new SettlementStats(10,0,0,0,SettlementStats.Availability.COMPLETE,List.of()),TownLedgerSnapshot.BellState.PRESENT,0).withFood(f);
        var packet=new TownLedgerSnapshotPayload(1,base,TownLedgerSnapshotPayload.Error.NONE);var b=new FriendlyByteBuf(Unpooled.buffer());
        try{TownLedgerSnapshotPayload.STREAM_CODEC.encode(b,packet);assertEquals(packet,TownLedgerSnapshotPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void debugFoodWorksWithCurrentTownAndExplicitUuid() throws Exception {
        var w=stocked();w.unavailableChunks.add("1:1");var food=w.scan(10);
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        var event=mock(net.neoforged.neoforge.event.RegisterCommandsEvent.class);when(event.getDispatcher()).thenReturn(dispatcher);
        dev.conner.hometown.command.HometownDebugCommands.register(event);
        var source=mock(net.minecraft.commands.CommandSourceStack.class);when(source.hasPermission(2)).thenReturn(true);
        var server=mock(net.minecraft.server.MinecraftServer.class);when(source.getServer()).thenReturn(server);when(server.getLevel(w.town.dimension())).thenReturn(w.level);
        var player=mock(net.minecraft.server.level.ServerPlayer.class);when(source.getPlayerOrException()).thenReturn(player);
        when(player.level()).thenReturn(w.level);when(w.level.dimension()).thenReturn(w.town.dimension());
        when(player.position()).thenReturn(new net.minecraft.world.phys.Vec3(8,64,8));when(player.blockPosition()).thenReturn(w.town.bellPosition());
        var data=mock(HometownSavedData.class);when(data.all()).thenReturn(List.of(w.town));
        var stats=new SettlementStats(10,0,0,0,SettlementStats.Availability.PARTIAL,List.of());
        var messages=new ArrayList<String>();
        doAnswer(c->{java.util.function.Supplier<net.minecraft.network.chat.Component> value=c.getArgument(0);messages.add(value.get().getString());return null;})
                .when(source).sendSuccess(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(false));
        try(var saved=mockStatic(HometownSavedData.class);var rules=mockStatic(SettlementValidator.Rules.class);var foods=mockStatic(FoodRules.class);
                var residents=mockStatic(SettlementScanner.class);var scanner=mockStatic(FoodScanner.class)) {
            saved.when(()->HometownSavedData.get(server)).thenReturn(data);when(data.getSettlement(w.town.id())).thenReturn(Optional.of(w.town));
            rules.when(SettlementValidator.Rules::current).thenReturn(new SettlementValidator.Rules(8,8,0,0,true,true));
            foods.when(FoodRules::current).thenReturn(FoodRules.DEFAULT);
            residents.when(()->SettlementScanner.scan(w.level,w.town,8)).thenReturn(stats);
            scanner.when(()->FoodScanner.scan(w.level,w.town,stats,8,FoodRules.DEFAULT)).thenReturn(food);
            assertEquals(1,dispatcher.execute("hometown debug food",source));
            assertEquals(1,dispatcher.execute("hometown debug food "+w.town.id(),source));
            assertEquals(2,messages.size());assertEquals(messages.get(0),messages.get(1));assertTrue(messages.get(0).contains("Scan Status: PARTIAL"));
        }
    }
    @Test void debugFoodStillRequiresOperatorPermission() {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        var event=mock(net.neoforged.neoforge.event.RegisterCommandsEvent.class);when(event.getDispatcher()).thenReturn(dispatcher);
        dev.conner.hometown.command.HometownDebugCommands.register(event);
        var source=mock(net.minecraft.commands.CommandSourceStack.class);when(source.hasPermission(2)).thenReturn(false);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute("hometown debug food",source));
    }

}
