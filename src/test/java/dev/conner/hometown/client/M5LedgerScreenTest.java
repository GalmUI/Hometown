package dev.conner.hometown.client;

import dev.conner.hometown.comfort.ComfortSnapshot;
import dev.conner.hometown.food.FoodRules;
import dev.conner.hometown.history.*;
import dev.conner.hometown.housing.HousingSnapshot;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.prosperity.ProsperitySnapshot;
import dev.conner.hometown.safety.SafetySnapshot;
import dev.conner.hometown.settlement.SettlementStats;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class M5LedgerScreenTest {
    @Test void prosperityIsLocalOnlyAndHistoryUsesDedicatedCachedPaging() throws Exception {
        var fixture=fixture();var screen=screen();var sent=new ArrayList<CustomPacketPayload>();
        try(var packets=mockStatic(PacketDistributor.class)) {
            packets.when(()->PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(call->{sent.add(call.getArgument(0));return null;});
            screen.requestPage(0);
            var request=(RequestTownLedgerPayload)sent.getFirst();
            screen.receive(new TownLedgerSnapshotPayload(request.requestId(),fixture.snapshot,TownLedgerSnapshotPayload.Error.NONE));
            assertEquals(1,sent.size());

            var prosperity=prosperity(fixture.meta,70);
            screen.receiveProsperity(new ProsperitySnapshotPayload(request.requestId(),prosperity));
            assertSame(prosperity,field(screen,"prosperity"));
            @SuppressWarnings("unchecked") var development=(List<net.minecraft.client.gui.components.Button>)field(screen,"developmentButtons");
            development.get(DevelopmentSection.PROSPERITY.ordinal()).onPress();
            assertEquals(1,sent.size(),"Prosperity navigation must not request or rescan anything");

            var staleMeta=new ObservationMetadata(fixture.id,"minecraft:overworld",fixture.meta.requestGeneration()+1,100,1,2,3);
            screen.receiveProsperity(new ProsperitySnapshotPayload(request.requestId(),prosperity(staleMeta,99)));
            assertSame(prosperity,field(screen,"prosperity"),"stale Prosperity companion must be rejected");

            var event=new HistoryEvent(2,fixture.id,HistoryEvent.Type.POPULATION_CHANGED,300,400,0,7,
                    HistoryEvent.Type.POPULATION_CHANGED.translationKey,
                    Map.of("previousPopulation",HistoryArgument.intValue(3),"newPopulation",HistoryArgument.intValue(4)));
            var history=new HistorySnapshotPayload(request.requestId(),fixture.id,fixture.meta.requestGeneration(),0,2,true,List.of(event));
            screen.receiveHistory(history);assertSame(history,field(screen,"history"));
            set(screen,"activeTab",3);invokeUpdateButtons(screen);
            ((net.minecraft.client.gui.components.Button)field(screen,"next")).onPress();
            assertEquals(2,sent.size());assertInstanceOf(RequestHistoryPagePayload.class,sent.getLast());
            var page=(RequestHistoryPagePayload)sent.getLast();assertEquals(1,page.page());assertEquals(fixture.meta.requestGeneration(),page.generation());
            assertEquals(1,sent.stream().filter(RequestTownLedgerPayload.class::isInstance).count(),"History paging must not create a fresh Ledger request");

            var staleHistory=new HistorySnapshotPayload(request.requestId(),fixture.id,fixture.meta.requestGeneration()+1,0,1,true,List.of());
            screen.receiveHistory(staleHistory);assertSame(history,field(screen,"history"),"stale History page must be rejected");
        }
    }

    private static TownLedgerScreen screen() throws Exception {
        var screen=new TownLedgerScreen(InteractionHand.MAIN_HAND);var font=mock(Font.class);
        when(font.width(anyString())).thenAnswer(call->((String)call.getArgument(0)).length()*6);
        when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(call->{String s=call.getArgument(0);int w=call.getArgument(1);return s.substring(0,Math.min(s.length(),Math.max(0,w/6)));});
        Field fontField=Screen.class.getDeclaredField("font");fontField.setAccessible(true);fontField.set(screen,font);
        screen.width=800;screen.height=500;var init=TownLedgerScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen);return screen;
    }
    private static Fixture fixture(){
        UUID id=UUID.randomUUID();var meta=new ObservationMetadata(id,"minecraft:overworld",7,100,1,2,3);
        var safety=SafetySnapshot.unavailable(meta);var comfort=ComfortSnapshot.unavailable(meta);
        var snapshot=new TownLedgerSnapshot(id,"Oured","GalmUI",0,"minecraft:overworld",BlockPos.ZERO,TownLedgerSnapshot.BellState.PRESENT,
                SettlementStats.Availability.COMPLETE,4,4,2,2,0,1,List.of(),new HousingSnapshot(4,4,4,0,4,4,0,0,0,true),
                FoodRules.DEFAULT.snapshot(4,1,1,1,560),safety,comfort);
        return new Fixture(id,meta,snapshot);
    }
    private static ProsperitySnapshot prosperity(ObservationMetadata meta,double index){
        var components=new ArrayList<ProsperitySnapshot.Component>();for(var type:ProsperitySnapshot.ComponentType.values())components.add(new ProsperitySnapshot.Component(type,
                ProsperitySnapshot.ComponentStatus.COMPLETE,OptionalDouble.of(index),OptionalDouble.of(index),20,OptionalDouble.of(index*20),Set.of()));
        var band=index<25?ProsperitySnapshot.Band.STARTING:index<50?ProsperitySnapshot.Band.DEVELOPING:index<75?ProsperitySnapshot.Band.ESTABLISHED:ProsperitySnapshot.Band.FLOURISHING;
        return new ProsperitySnapshot(meta,true,ProsperitySnapshot.Status.COMPLETE,components,100,OptionalDouble.of(index*100),OptionalDouble.of(index),Optional.of(band),List.of());
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void invokeUpdateButtons(TownLedgerScreen screen)throws Exception{var m=TownLedgerScreen.class.getDeclaredMethod("updateButtons");m.setAccessible(true);m.invoke(screen);}
    private record Fixture(UUID id,ObservationMetadata meta,TownLedgerSnapshot snapshot){}
}
