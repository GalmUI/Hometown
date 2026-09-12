package dev.conner.hometown.client;

import dev.conner.hometown.prosperity.ProsperitySnapshot;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Presentation-only Prosperity view. It never observes the world or requests data. */
final class ProsperityLedgerView {
    private ProsperityLedgerView() {}

    static void render(GuiGraphics g, Font font, ProsperitySnapshot p,
            int x, int right, int y, int column, int ink, int muted, int warning) {
        line(g,font,Component.translatable("hometown.prosperity.title"),x,y,column,ink);
        line(g,font,Component.translatable("hometown.prosperity.index"),x,y+14,column,ink);

        Component state=switch(p.status()) {
            case COMPLETE -> Component.translatable("hometown.prosperity.band."+p.band().orElseThrow().name().toLowerCase(Locale.ROOT));
            case NO_RESIDENTS -> Component.translatable("hometown.prosperity.status.no_residents");
            case INCOMPLETE -> Component.translatable("hometown.prosperity.status.incomplete");
            case DISABLED -> Component.translatable("hometown.prosperity.status.disabled");
        };
        line(g,font,state,x,y+28,column,p.status()==ProsperitySnapshot.Status.COMPLETE?ink:warning);
        line(g,font,p.developmentIndex().isPresent()
                ?Component.translatable("hometown.prosperity.percent",p.displayedIndex())
                :Component.translatable("hometown.prosperity.na"),x,y+42,column,ink);
        if(p.developmentIndex().isPresent()) {
            g.fill(x,y+54,x+column,y+59,0xFFBCA27D);
            int filled=(int)Math.round(column*Math.clamp(p.developmentIndex().getAsDouble(),0,100)/100.0);
            if(filled>0)g.fill(x,y+54,x+filled,y+59,0xFF817644);
        }
        if(p.status()==ProsperitySnapshot.Status.INCOMPLETE) {
            line(g,font,Component.translatable("hometown.prosperity.missing"),x,y+72,column,warning);
            int row=0;
            for(var type:p.missingRequiredInputs()) {
                line(g,font,Component.literal("• ").append(name(type)),x,y+86+row*13,column,muted);
                if(++row>=4)break;
            }
        } else if(p.status()==ProsperitySnapshot.Status.COMPLETE) {
            line(g,font,Component.translatable("hometown.prosperity.weight_total",p.totalEnabledWeight()),x,y+72,column,muted);
            g.drawWordWrap(font,Component.translatable("hometown.prosperity.authoritative"),x,y+88,column,muted);
        } else {
            line(g,font,Component.translatable("hometown.prosperity.no_index"),x,y+72,column,muted);
        }

        line(g,font,Component.translatable("hometown.prosperity.components"),right,y,column,ink);
        int row=0;
        for(var c:p.components()) {
            int ry=y+16+row*25;
            line(g,font,name(c.type()),right,ry,column,ink);
            Component detail;
            int color=ink;
            if(c.status()==ProsperitySnapshot.ComponentStatus.COMPLETE) {
                detail=Component.translatable("hometown.prosperity.component.complete",
                        round(c.normalizedValue().orElseThrow()),formatWeightShare(c.configuredWeight(),p.totalEnabledWeight()),
                        formatIndexPoints(c.weightedContribution().orElseThrow(),p.totalEnabledWeight()));
            } else if(c.status()==ProsperitySnapshot.ComponentStatus.EXCLUDED) {
                detail=Component.translatable("hometown.prosperity.component.excluded");color=muted;
            } else {
                detail=Component.translatable("hometown.prosperity.component."+c.status().name().toLowerCase(Locale.ROOT));color=warning;
            }
            line(g,font,detail,right,ry+11,column,color);
            row++;
        }
    }

    static Component name(ProsperitySnapshot.ComponentType type) {
        return Component.translatable("hometown.prosperity.component_name."+type.name().toLowerCase(Locale.ROOT));
    }
    static String formatWeightShare(int configuredWeight,int totalEnabledWeight){
        if(totalEnabledWeight<=0)throw new IllegalArgumentException("Prosperity enabled weight must be positive");
        double value=100.0*configuredWeight/totalEnabledWeight;
        double whole=Math.rint(value);
        return (Math.abs(value-whole)<1.0e-9?String.format(Locale.ROOT,"%.0f",whole):String.format(Locale.ROOT,"%.1f",value))+"%";
    }
    static String formatIndexPoints(double weightedContribution,int totalEnabledWeight){
        if(totalEnabledWeight<=0)throw new IllegalArgumentException("Prosperity enabled weight must be positive");
        return String.format(Locale.ROOT,"%.1f pts",weightedContribution/totalEnabledWeight);
    }
    private static int round(double value){return (int)Math.floor(value+0.5d);}
    private static void line(GuiGraphics g,Font font,Component text,int x,int y,int width,int color){
        String value=text.getString();String shown=font.width(value)>width?font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…":value;
        g.drawString(font,shown,x,y,color,false);
    }
}
