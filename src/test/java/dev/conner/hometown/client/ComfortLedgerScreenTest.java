package dev.conner.hometown.client;

import com.google.gson.JsonParser;
import dev.conner.hometown.comfort.*;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.settlement.*;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static dev.conner.hometown.comfort.ComfortSnapshot.*;

class ComfortLedgerScreenTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    private TownLedgerScreen screen() throws Exception {
        var screen=mock(TownLedgerScreen.class,withSettings().spiedInstance(new TownLedgerScreen(InteractionHand.MAIN_HAND))
            .defaultAnswer(call->List.of("renderBlurredBackground","renderMenuBackground","renderPanorama").contains(call.getMethod().getName())?null:Answers.CALLS_REAL_METHODS.answer(call)));
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*6);
        when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(c->{String s=c.getArgument(0);return s.substring(0,Math.min(s.length(),Math.max(0,(int)c.getArgument(1)/6)));});
        var ff=Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(screen,font);
        var mf=Screen.class.getDeclaredField("minecraft");mf.setAccessible(true);mf.set(screen,mock(Minecraft.class));
        screen.width=480;screen.height=270;var init=TownLedgerScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen);return screen;
    }
    private List<String> draw(TownLedgerScreen screen){
        var text=new ArrayList<String>();var g=mock(GuiGraphics.class,call->{String m=call.getMethod().getName();if(m.equals("drawString")||m.equals("drawWordWrap")||m.equals("drawCenteredString")){Object v=call.getArgument(1);text.add(v instanceof FormattedText f?f.getString():v.toString());}return Answers.RETURNS_DEFAULTS.answer(call);});
        screen.render(g,0,0,0);return text;
    }
    private ComfortSettings allEnabled(){
        var map=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values())map.put(c,new ComfortSettings.Category(true,c.defaultWeight));
        return new ComfortSettings(true,map,512,65536);
    }
    private TownLedgerSnapshot snapshot(){
        var town=new Settlement(UUID.randomUUID(),"Comforton",Level.OVERWORLD,new BlockPos(0,64,0),16,UUID.randomUUID(),"Founder",0);
        var base=TownLedgerSnapshot.of(town,new SettlementStats(2,2,0,0,SettlementStats.Availability.COMPLETE,List.of()),TownLedgerSnapshot.BellState.PRESENT,0)
            .withHousing(new dev.conner.hometown.housing.HousingSnapshot(2,2,2,0,2,2,0,0,0,true));
        var settings=allEnabled();var hits=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);for(var c:ComfortCategory.values())hits.put(c,1);
        var a=ComfortEvaluator.room(new BlockPos(0,64,0),1,5,4,Status.COMPLETE,Map.of(),hits,0,0,settings);
        var b=ComfortEvaluator.room(new BlockPos(8,64,0),1,5,4,Status.COMPLETE,Map.of(),hits,0,0,settings);
        return base.withComfort(ComfortEvaluator.town(base.metadata(),settings,true,2,List.of(a,b),Map.of(),2,17,262144));
    }

    @Test void C10_categoriesAndRoomDetailsReuseSnapshotAndPageRooms() throws Exception {
        Language original=Language.getInstance();var translations=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/hometown/lang/en_us.json"))).getAsJsonObject();
        Language.inject(new Language(){public String getOrDefault(String k,String f){return translations.has(k)?translations.get(k).getAsString():original.getOrDefault(k,f);}public boolean has(String k){return translations.has(k)||original.has(k);}public boolean isDefaultRightToLeft(){return false;}public FormattedCharSequence getVisualOrder(FormattedText t){return FormattedCharSequence.forward(t.getString(),Style.EMPTY);}});
        try(var packets=mockStatic(PacketDistributor.class)){
            var requests=new ArrayList<RequestTownLedgerPayload>();packets.when(()->PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(c->{requests.add(c.getArgument(0));return null;});
            var screen=screen();screen.requestPage(0);screen.receive(new TownLedgerSnapshotPayload(requests.getLast().requestId(),snapshot(),TownLedgerSnapshotPayload.Error.NONE));
            ((Button)screen.children().get(2)).onPress();((Button)screen.children().get(10)).onPress();
            int before=requests.size();var categories=draw(screen);
            assertTrue(categories.contains("Residential Comfort"));assertTrue(categories.contains("100%"));assertTrue(categories.contains("Well furnished"));
            assertTrue(categories.contains("Rooms Assessed: 2 / 2"));
            for(var name:List.of("Storage","Lighting","Decor","Books","Plants","Amenities","Seating","Tables"))assertTrue(categories.stream().anyMatch(s->s.startsWith(name+":")),name);
            Button details=(Button)screen.children().get(13);assertTrue(details.visible);details.onPress();
            var first=draw(screen);assertTrue(first.contains("Room 1 of 2"));assertTrue(first.contains("Beds: 1"));assertTrue(first.contains("Comfort: 100%"));
            Button previous=(Button)screen.children().get(4),next=(Button)screen.children().get(5);assertTrue(next.visible&&next.active);assertFalse(previous.active);next.onPress();
            assertTrue(draw(screen).contains("Room 2 of 2"));assertTrue(previous.active);assertFalse(next.active);
            Button back=(Button)screen.children().get(14);assertTrue(back.visible);back.onPress();assertTrue(draw(screen).contains("Comfort Categories"));
            assertEquals(before,requests.size(),"Comfort subnavigation must not request another server snapshot");
        } finally {Language.inject(original);}
    }
}
