package dev.conner.hometown.client;

import dev.conner.hometown.commerce.CommerceSnapshot;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Presentation helper only; no requests or world access. */
final class CommerceLedgerView {
    private CommerceLedgerView() {}
    static int pages(CommerceSnapshot snapshot){return Math.max(1,(snapshot.professionCounts().size()+5)/6);}

    static void render(GuiGraphics g,Font font,CommerceSnapshot c,int page,int x,int right,int y,int column,int ink,int muted,int warning){
        line(g,font,Component.translatable("hometown.commerce.title"),x,y,column,ink);
        line(g,font,Component.translatable(c.scanStatus()==CommerceSnapshot.Status.PARTIAL?"hometown.commerce.observed_employment":"hometown.commerce.employment"),x,y+14,column,ink);
        Component state=switch(c.employmentState()){
            case INCOMPLETE -> Component.translatable("hometown.commerce.state.incomplete");
            case DISABLED -> Component.translatable("hometown.commerce.state.disabled");
            case NO_ELIGIBLE_ADULTS -> Component.translatable("hometown.commerce.state.no_eligible_adults");
            default -> Component.translatable("hometown.commerce.state."+c.employmentState().name().toLowerCase(Locale.ROOT));
        };
        line(g,font,state,x,y+28,column,c.scanStatus()==CommerceSnapshot.Status.COMPLETE?ink:warning);
        var shown=c.employmentPercent().isPresent()?c.employmentPercent():c.observedEmploymentPercent();
        line(g,font,shown.isPresent()?Component.translatable("hometown.commerce.percent",round(shown.getAsDouble())):Component.literal("N/A"),x,y+42,column,ink);
        if(shown.isPresent()){
            g.fill(x,y+54,x+column,y+59,0xFFBCA27D);
            int filled=(int)Math.round(column*Math.clamp(shown.getAsDouble(),0,100)/100.0);if(filled>0)g.fill(x,y+54,x+filled,y+59,0xFF817644);
        }
        if(c.employmentState()==CommerceSnapshot.EmploymentState.NO_ELIGIBLE_ADULTS)
            line(g,font,Component.translatable("hometown.commerce.no_eligible"),x,y+70,column,muted);
        else {
            line(g,font,Component.translatable("hometown.commerce.employed",c.employedAdults()),x,y+70,column,ink);
            line(g,font,Component.translatable("hometown.commerce.eligible",c.eligibleAdults()),x,y+84,column,ink);
            line(g,font,Component.translatable("hometown.commerce.unemployed",c.unemployedAdults()),x,y+98,column,ink);
        }
        line(g,font,Component.translatable("hometown.commerce.scope"),x,y+118,column,muted);
        if(c.scanStatus()==CommerceSnapshot.Status.PARTIAL)line(g,font,Component.translatable("hometown.commerce.partial"),x,y+132,column,warning);

        line(g,font,Component.translatable("hometown.commerce.professions"),right,y,column,ink);
        line(g,font,Component.translatable("hometown.commerce.diversity",c.professionDiversity()),right,y+14,column,ink);
        var entries=new ArrayList<>(c.professionCounts().entrySet());
        entries.sort(Comparator.<Map.Entry<ResourceLocation,Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry->professionLabel(entry.getKey()).getString(),String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry->entry.getKey().toString()));
        int pages=Math.max(1,(entries.size()+5)/6),safe=Math.clamp(page,0,pages-1),start=safe*6;
        if(entries.isEmpty())line(g,font,Component.translatable("hometown.commerce.no_professions"),right,y+34,column,muted);
        for(int i=start;i<Math.min(start+6,entries.size());i++){
            var entry=entries.get(i);line(g,font,Component.translatable("hometown.commerce.profession_row",professionLabel(entry.getKey()),entry.getValue()),right,y+34+(i-start)*17,column,ink);
        }
        if(pages>1)line(g,font,Component.translatable("hometown.commerce.page",safe+1,pages),right,y+139,column,muted);
    }
    static Component professionLabel(ResourceLocation id){
        String key="entity."+id.getNamespace()+".villager."+id.getPath();
        return I18n.exists(key)?Component.translatable(key):Component.literal(id.toString());
    }
    private static int round(double value){return (int)Math.floor(value+0.5d);}
    private static void line(GuiGraphics g,Font font,Component text,int x,int y,int width,int color){
        String value=text.getString();String shown=font.width(value)>width?font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…":value;g.drawString(font,shown,x,y,color,false);
    }
}
