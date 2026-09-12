package dev.conner.hometown.client;

import com.google.gson.JsonParser;
import dev.conner.hometown.commerce.CommerceSnapshot;
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
import net.minecraft.resources.ResourceLocation;
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

class CommerceLedgerScreenTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private TownLedgerScreen screen()throws Exception{
        var screen=mock(TownLedgerScreen.class,withSettings().spiedInstance(new TownLedgerScreen(InteractionHand.MAIN_HAND))
                .defaultAnswer(call->List.of("renderBlurredBackground","renderMenuBackground","renderPanorama").contains(call.getMethod().getName())?null:Answers.CALLS_REAL_METHODS.answer(call)));
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*6);
        when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(c->{String s=c.getArgument(0);return s.substring(0,Math.min(s.length(),Math.max(0,(int)c.getArgument(1)/6)));});
        var ff=Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(screen,font);var mf=Screen.class.getDeclaredField("minecraft");mf.setAccessible(true);mf.set(screen,mock(Minecraft.class));
        screen.width=480;screen.height=270;var init=TownLedgerScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen);return screen;
    }
    private List<String> draw(TownLedgerScreen screen){
        var text=new ArrayList<String>();var g=mock(GuiGraphics.class,call->{String m=call.getMethod().getName();if(m.equals("drawString")||m.equals("drawWordWrap")||m.equals("drawCenteredString")){Object v=call.getArgument(1);text.add(v instanceof FormattedText f?f.getString():v.toString());}return Answers.RETURNS_DEFAULTS.answer(call);});screen.render(g,0,0,0);return text;
    }
    private Button button(TownLedgerScreen screen,String label){return screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();}
    private TownLedgerSnapshot base(){
        var town=new Settlement(UUID.randomUUID(),"Tradewick",Level.OVERWORLD,new BlockPos(8,64,8),16,UUID.randomUUID(),"Founder",0);
        return TownLedgerSnapshot.of(town,new SettlementStats(13,4,7,7,SettlementStats.Availability.COMPLETE,List.of()),TownLedgerSnapshot.BellState.PRESENT,0);
    }
    private CommerceSnapshot complete(TownLedgerSnapshot base){
        var counts=new LinkedHashMap<ResourceLocation,Integer>();
        for(String id:List.of("minecraft:farmer","minecraft:librarian","minecraft:cleric","minecraft:toolsmith","minecraft:armorer","minecraft:butcher","example:engineer"))counts.put(ResourceLocation.parse(id),1);
        return new CommerceSnapshot(base.metadata(),true,CommerceSnapshot.Status.COMPLETE,Map.of(),13,10,7,3,2,1,
                OptionalDouble.of(70),OptionalDouble.empty(),CommerceSnapshot.EmploymentState.ACTIVE,7,counts,13,0);
    }
    private CommerceSnapshot partial(TownLedgerSnapshot base){
        return new CommerceSnapshot(base.metadata(),true,CommerceSnapshot.Status.PARTIAL,Map.of(CommerceSnapshot.Reason.RESIDENT_DATA_INCOMPLETE,1),8,8,6,2,0,0,
                OptionalDouble.empty(),OptionalDouble.of(75),CommerceSnapshot.EmploymentState.INCOMPLETE,1,Map.of(ResourceLocation.parse("minecraft:farmer"),6),8,0);
    }

    @Test void E09_commerceUsesOpenedSnapshotPagesLocallyAndLabelsPartialData()throws Exception{
        Language original=Language.getInstance();var translations=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/hometown/lang/en_us.json"))).getAsJsonObject();
        Language.inject(new Language(){public String getOrDefault(String k,String f){return translations.has(k)?translations.get(k).getAsString():original.getOrDefault(k,f);}public boolean has(String k){return translations.has(k)||original.has(k);}public boolean isDefaultRightToLeft(){return false;}public FormattedCharSequence getVisualOrder(FormattedText t){return FormattedCharSequence.forward(t.getString(),Style.EMPTY);}});
        try(var packets=mockStatic(PacketDistributor.class)){
            var requests=new ArrayList<RequestTownLedgerPayload>();packets.when(()->PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(c->{if(c.getArgument(0) instanceof RequestTownLedgerPayload r)requests.add(r);return null;});
            var screen=screen();var base=base();screen.requestPage(0);int requestId=requests.getLast().requestId();
            screen.receive(new TownLedgerSnapshotPayload(requestId,base,TownLedgerSnapshotPayload.Error.NONE));screen.receiveCommerce(new CommerceSnapshotPayload(requestId,complete(base)));
            button(screen,"Development").onPress();button(screen,"Commerce").onPress();int before=requests.size();
            var first=draw(screen);assertTrue(first.contains("Commerce"));assertTrue(first.contains("Employment"));assertTrue(first.contains("ACTIVE"));assertTrue(first.contains("70%"));
            assertTrue(first.contains("Employed Adults: 7"));assertTrue(first.contains("Eligible Adults: 10"));assertTrue(first.contains("Unemployed Adults: 3"));assertTrue(first.contains("Diversity: 7"));
            assertTrue(first.stream().anyMatch(text->text.startsWith("Observed professions")));assertTrue(first.contains("Professions page 1 / 2"));
            Button next=screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("→")).map(w->(Button)w).findFirst().orElseThrow();
            assertTrue(next.visible&&next.active);next.onPress();var second=draw(screen);assertTrue(second.contains("Professions page 2 / 2"));assertEquals(before,requests.size(),"Commerce tab and profession pagination must not request or rescan");
            screen.receiveCommerce(new CommerceSnapshotPayload(requestId,partial(base)));var partial=draw(screen);
            assertTrue(partial.contains("Observed Employment"));assertTrue(partial.contains("INCOMPLETE DATA"));assertTrue(partial.contains("75%"));assertTrue(partial.contains("Resident data is partial."));
        }finally{Language.inject(original);}
    }
}
