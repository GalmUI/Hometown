package dev.conner.hometown.settlement;

import dev.conner.hometown.TestServerConfig;
import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.network.*;
import dev.conner.hometown.housing.*;
import dev.conner.hometown.food.*;
import dev.conner.hometown.safety.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LedgerObservationTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void coalescingCooldownPaginationFreshnessAndInvalidation() {
        var server=mock(MinecraftServer.class);var level=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);
        when(server.isSameThread()).thenReturn(true);when(server.overworld()).thenReturn(level);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);when(level.getChunkSource()).thenReturn(chunks);
        var town=new Settlement(UUID.randomUUID(),"Observe",Level.OVERWORLD,BlockPos.ZERO,8,UUID.randomUUID(),"Owner",0);
        var store=new HometownSavedData();store.addSettlement(town);store.setDirty(false);
        var component=DataComponentType.<SettlementIdComponent>builder().persistent(SettlementIdComponent.CODEC).build();
        var alice=player(server,component,town);var bob=player(server,component,town);
        var stats=new SettlementStats(5,0,0,0,SettlementStats.Availability.COMPLETE,
            java.util.stream.IntStream.range(0,5).mapToObj(i->new dev.conner.hometown.network.data.ResidentSummary("V"+i,"unemployed",false)).toList());
        try(var config=new TestServerConfig();var data=mockStatic(HometownSavedData.class);
            var scanner=mockStatic(SettlementScanner.class);var housing=mockStatic(HousingScanner.class);
            var food=mockStatic(FoodScanner.class)) {
            data.when(()->HometownSavedData.get(server)).thenReturn(store);
            scanner.when(()->SettlementScanner.scan(level,town,32)).thenReturn(stats);
            housing.when(()->HousingScanner.observe(level,town,stats,32)).thenReturn(new HousingScanner.Observation(new HousingSnapshot(5,0,0,0,0,0,0,0,0,true),Set.of()));
            var reserves=FoodRules.DEFAULT.snapshot(5,0,0,0,0);
            food.when(()->FoodScanner.observe(eq(level),eq(town),eq(stats),eq(32),any())).thenReturn(new FoodObservation(reserves,List.of()));
            when(level.getGameTime()).thenReturn(100L);
            var first=open(alice,component).snapshot();
            assertEquals(reserves,first.food(),"M3 one-pass observation must preserve the protected Reserves snapshot");
            assertEquals(100,first.metadata().observedGameTime());
            assertEquals(first.metadata(),open(bob,component).snapshot().metadata());
            scanner.verify(()->SettlementScanner.scan(level,town,32),times(1));
            when(level.getGameTime()).thenReturn(139L);
            assertEquals(first.metadata(),open(alice,component).snapshot().metadata());
            scanner.verify(()->SettlementScanner.scan(level,town,32),times(1));
            when(level.getGameTime()).thenReturn(140L);
            var fresh=open(alice,component).snapshot();assertNotEquals(first.metadata().requestGeneration(),fresh.metadata().requestGeneration());
            assertEquals(140,fresh.metadata().observedGameTime());scanner.verify(()->SettlementScanner.scan(level,town,32),times(2));
            when(level.getGameTime()).thenReturn(200L);
            var page=TownLedgerService.respond(alice,new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,1,2,fresh.metadata().requestGeneration()),Items.WRITTEN_BOOK,component);
            assertEquals(1,page.snapshot().residentPage());assertEquals("V4",page.snapshot().residents().getFirst().name());
            assertEquals(fresh.metadata(),page.snapshot().metadata());scanner.verify(()->SettlementScanner.scan(level,town,32),times(2));
            assertEquals(TownLedgerSnapshotPayload.Error.WAIT,TownLedgerService.respond(alice,
                new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,0,3,first.metadata().requestGeneration()),Items.WRITTEN_BOOK,component).error());
            TownLedgerService.respond(alice,new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,-1,4,fresh.metadata().requestGeneration()),Items.WRITTEN_BOOK,component);
            assertEquals(TownLedgerSnapshotPayload.Error.WAIT,TownLedgerService.respond(alice,
                new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,0,5,fresh.metadata().requestGeneration()),Items.WRITTEN_BOOK,component).error());
            var reopen=open(alice,component).snapshot();assertEquals(200,reopen.metadata().observedGameTime());
            config.set("safety.minimumResidentialBlockLight",2);
            var revised=open(alice,component).snapshot();
            assertNotEquals(reopen.metadata().configurationRevision(),revised.metadata().configurationRevision());
            assertNotEquals(reopen.metadata().requestGeneration(),revised.metadata().requestGeneration());
            assertEquals(2,revised.safety().minimumBlockLight());
            var registry=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            var originalTags=new java.util.HashMap<net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>,java.util.List<net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>>>>();
            registry.getTags().forEach(pair->originalTags.put(pair.getFirst(),pair.getSecond().stream().toList()));
            try {
                var changedTags=new java.util.HashMap<>(originalTags);
                changedTags.put(SafetyCollector.THREATS,java.util.List.of(net.minecraft.world.entity.EntityType.ZOMBIE.builtInRegistryHolder()));
                registry.bindTags(changedTags);
                var reloaded=open(alice,component).snapshot();
                assertNotEquals(revised.metadata().dataRevision(),reloaded.metadata().dataRevision());
                assertNotEquals(revised.metadata().requestGeneration(),reloaded.metadata().requestGeneration());
                assertEquals(TownLedgerSnapshotPayload.Error.WAIT,TownLedgerService.respond(alice,
                    new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,0,7,revised.metadata().requestGeneration()),Items.WRITTEN_BOOK,component).error());
            } finally { registry.bindTags(originalTags); }
            int beforeDebug=store.all().size();
            TownLedgerService.debugSafety(server,town);
            TownLedgerService.debugSafety(server,town);
            assertEquals(beforeDebug,store.all().size());
            assertFalse(store.isDirty());
            TownLedgerService.release(alice);
            assertEquals(TownLedgerSnapshotPayload.Error.WAIT,TownLedgerService.respond(alice,
                new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,0,6,revised.metadata().requestGeneration()),Items.WRITTEN_BOOK,component).error());
        } finally {TownLedgerService.release(server);}
    }
    private ServerPlayer player(MinecraftServer server,DataComponentType<SettlementIdComponent> component,Settlement town){
        var p=mock(ServerPlayer.class);when(p.getServer()).thenReturn(server);when(p.isAlive()).thenReturn(true);when(p.getUUID()).thenReturn(UUID.randomUUID());
        var item=new ItemStack(Items.WRITTEN_BOOK);item.set(component,new SettlementIdComponent(town.id()));
        when(p.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(item);return p;
    }
    private TownLedgerSnapshotPayload open(ServerPlayer p,DataComponentType<SettlementIdComponent> component){
        return TownLedgerService.respond(p,new RequestTownLedgerPayload(InteractionHand.MAIN_HAND,0,1),Items.WRITTEN_BOOK,component);
    }
}
