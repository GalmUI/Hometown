package dev.conner.hometown.client;

import com.google.gson.JsonParser;
import dev.conner.hometown.food.*;
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

class FoodM3LedgerScreenTest {
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
        var town=new Settlement(UUID.randomUUID(),"Foodtown",Level.OVERWORLD,new BlockPos(8,64,8),16,UUID.randomUUID(),"Founder",0);
        return TownLedgerSnapshot.of(town,new SettlementStats(10,2,0,0,SettlementStats.Availability.COMPLETE,List.of()),TownLedgerSnapshot.BellState.PRESENT,0)
                .withFood(FoodRules.DEFAULT.snapshot(10,1,3,3,120));
    }
    private FoodVarietySnapshot variety(TownLedgerSnapshot base){
        var totals=new EnumMap<FoodGroup,Long>(FoodGroup.class);var q=new EnumMap<FoodGroup,FoodVarietySnapshot.Qualification>(FoodGroup.class);
        for(var group:FoodGroup.values()){totals.put(group,group==FoodGroup.GRAINS||group==FoodGroup.VEGETABLES||group==FoodGroup.FRUIT?20L:0L);q.put(group,totals.get(group)>=20?FoodVarietySnapshot.Qualification.TRUE:FoodVarietySnapshot.Qualification.FALSE);}
        return new FoodVarietySnapshot(base.metadata(),true,10,true,totals,EnumSet.allOf(FoodGroup.class),OptionalLong.of(20),q,3,0,OptionalDouble.of(60),OptionalDouble.of(60),FoodVarietySnapshot.VarietyState.VARIED,0,FoodScanStatus.COMPLETE);
    }
    private FoodGrowingSnapshot growing(TownLedgerSnapshot base){
        var families=new ArrayList<FoodGrowingSnapshot.FamilyRecord>();for(int i=1;i<=5;i++)families.add(new FoodGrowingSnapshot.FamilyRecord(ResourceLocation.fromNamespaceAndPath("test","crop_"+i),i,OptionalInt.of(i/2),FoodGrowingSnapshot.MaturityStatus.ASSESSED));
        return new FoodGrowingSnapshot(base.metadata(),true,FoodGrowingSnapshot.Status.COMPLETE,Map.of(),15,6,0,5,families,1,1,1,3,100,103,262144);
    }
    @Test void F10_reservesVarietyGrowingReuseOneOpenedObservationAndPageFamilies()throws Exception{
        Language original=Language.getInstance();var translations=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/hometown/lang/en_us.json"))).getAsJsonObject();
        Language.inject(new Language(){public String getOrDefault(String k,String f){return translations.has(k)?translations.get(k).getAsString():original.getOrDefault(k,f);}public boolean has(String k){return translations.has(k)||original.has(k);}public boolean isDefaultRightToLeft(){return false;}public FormattedCharSequence getVisualOrder(FormattedText t){return FormattedCharSequence.forward(t.getString(),Style.EMPTY);}});
        try(var packets=mockStatic(PacketDistributor.class)){
            var requests=new ArrayList<RequestTownLedgerPayload>();packets.when(()->PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(c->{if(c.getArgument(0) instanceof RequestTownLedgerPayload r)requests.add(r);return null;});
            var screen=screen(),base=base();screen.requestPage(0);int requestId=requests.getLast().requestId();screen.receive(new TownLedgerSnapshotPayload(requestId,base,TownLedgerSnapshotPayload.Error.NONE));screen.receiveFoodM3(new FoodM3SnapshotPayload(requestId,variety(base),growing(base)));
            button(screen,"Development").onPress();button(screen,"Food").onPress();int before=requests.size();
            var reserves=draw(screen);assertTrue(reserves.contains("Food Security"));assertTrue(reserves.contains("Reserves"));assertTrue(reserves.contains("Variety"));assertTrue(reserves.contains("Growing"));
            button(screen,"Variety").onPress();var variety=draw(screen);assertTrue(variety.contains("Food Variety"));assertTrue(variety.contains("VARIED"));assertTrue(variety.contains("60%"));assertTrue(variety.contains("Food Groups: 3 / 5"));assertTrue(variety.contains("Stored Food Groups — Nutrition"));
            button(screen,"Growing").onPress();var growing=draw(screen);assertTrue(growing.contains("Growing Capacity"));assertTrue(growing.contains("Crops Observed: 15"));assertTrue(growing.contains("Crop Families: 5"));assertTrue(growing.contains("Crop 1"));assertTrue(growing.contains("Families page 1 / 2"));
            Button next=screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("→")).map(w->(Button)w).findFirst().orElseThrow();assertTrue(next.visible&&next.active);next.onPress();var second=draw(screen);assertTrue(second.contains("Crop 5"));assertTrue(second.contains("Families page 2 / 2"));
            button(screen,"Reserves").onPress();assertTrue(draw(screen).contains("Food Security"));assertEquals(before,requests.size(),"Food subnavigation and Growing pagination must not request or rescan");
        }finally{Language.inject(original);}
    }
}
