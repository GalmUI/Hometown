package dev.conner.hometown.client;

import dev.conner.hometown.history.HistoryArgument;
import dev.conner.hometown.history.HistoryEvent;
import dev.conner.hometown.network.HistorySnapshotPayload;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Renders one immutable cached History page. No world access and no state mutation. */
final class HistoryLedgerView {
    private HistoryLedgerView() {}

    static void render(GuiGraphics g,Font font,HistorySnapshotPayload history,
            int x,int right,int y,int column,int bookCenter,int footerY,int ink,int muted,int warning) {
        line(g,font,Component.translatable("hometown.history.title"),x,y,column,ink);
        if(!history.available()) {
            line(g,font,Component.translatable("hometown.history.unavailable"),x,y+20,column*2+34,warning);
            return;
        }
        if(history.events().isEmpty()) {
            line(g,font,Component.translatable("hometown.history.empty"),x,y+20,column*2+34,muted);
        }
        for(int i=0;i<history.events().size();i++) {
            var event=history.events().get(i);
            int px=i<4?x:right, row=i%4, py=y+18+row*35;
            line(g,font,Component.translatable("hometown.history.day",event.observedDay()),px,py,column,muted);
            wordWrap(g,font,eventText(event),px,py+11,column,ink,2);
        }
        if(history.totalPages()>1)
            g.drawCenteredString(font,Component.translatable("hometown.history.page",history.page()+1,history.totalPages()),bookCenter,footerY,muted);
    }

    static Component eventText(HistoryEvent event) {
        var a=event.arguments();
        return switch(event.type()) {
            case TOWN_FOUNDED -> Component.translatable(event.translationKey(),
                    text(a,"townName","Town"),text(a,"founderName","Unknown"));
            case TOWN_HALL_ESTABLISHED -> Component.translatable(event.translationKey(),
                    text(a,"townName","Town"));
            case POPULATION_CHANGED -> Component.translatable(event.translationKey(),
                    intValue(a,"previousPopulation"),intValue(a,"newPopulation"));
            case HOUSING_SHORTAGE_STARTED, HOUSING_SHORTAGE_RESOLVED -> Component.translatable(event.translationKey(),
                    intValue(a,"observedPopulation"),intValue(a,"observedEnclosedBeds"));
            case FOOD_SECURITY_CHANGED -> Component.translatable(event.translationKey(),
                    foodState(a,"previousSecurityState"),foodState(a,"newSecurityState"));
            case PROSPERITY_DEVELOPING_REACHED, PROSPERITY_ESTABLISHED_REACHED, PROSPERITY_FLOURISHING_REACHED -> Component.translatable(event.translationKey(),
                    text(a,"townName","Town"),roundedDouble(a,"developmentIndexAtObservation"));
        };
    }

    private static Object foodState(java.util.Map<String,HistoryArgument> args,String key) {
        var value=args.get(key);if(value==null)return "?";
        return Component.translatable("hometown.food.state."+value.value().toLowerCase(Locale.ROOT));
    }
    private static int intValue(java.util.Map<String,HistoryArgument> args,String key){var value=args.get(key);return value==null?0:Integer.parseInt(value.value());}
    private static int roundedDouble(java.util.Map<String,HistoryArgument> args,String key){var value=args.get(key);return value==null?0:(int)Math.floor(Double.parseDouble(value.value())+0.5d);}
    private static String text(java.util.Map<String,HistoryArgument> args,String key,String fallback){var value=args.get(key);return value==null?fallback:value.value();}
    private static void wordWrap(GuiGraphics g,Font font,Component text,int x,int y,int width,int color,int maxLines){
        var lines=font.split(text,width);for(int i=0;i<Math.min(maxLines,lines.size());i++)g.drawString(font,lines.get(i),x,y+i*10,color,false);
    }
    private static void line(GuiGraphics g,Font font,Component text,int x,int y,int width,int color){
        String value=text.getString();String shown=font.width(value)>width?font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…":value;
        g.drawString(font,shown,x,y,color,false);
    }
}
