package dev.conner.hometown.client;

import dev.conner.hometown.food.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Presentation helper only; no requests, world access, or derived game state. */
final class FoodM3LedgerView {
    private FoodM3LedgerView() {}
    static void variety(GuiGraphics g,Font font,FoodVarietySnapshot v,int x,int right,int y,int column,int ink,int muted,int warning) {
        line(g,font,Component.translatable("hometown.food.variety.title"),x,y,column,ink);
        Component state=switch(v.varietyState()) {
            case INCOMPLETE -> Component.translatable("hometown.food.variety.incomplete");
            case NO_RESIDENTS -> Component.translatable("hometown.food.variety.no_residents");
            case DISABLED -> Component.translatable("hometown.food.variety.disabled");
            default -> Component.translatable("hometown.food.variety.state."+v.varietyState().name().toLowerCase(java.util.Locale.ROOT));
        };
        line(g,font,state,x,y+14,column,v.authoritativeCoverage().isPresent()?ink:warning);
        var shown=v.authoritativeCoverage().isPresent()?v.authoritativeCoverage():v.observedCoverage();
        Component coverage=v.authoritativeCoverage().isPresent()?Component.translatable("hometown.food.variety.coverage",round(shown.getAsDouble())):
                v.observedCoverage().isPresent()?Component.translatable("hometown.food.variety.at_least",round(shown.getAsDouble())):Component.literal("N/A");
        line(g,font,coverage,x,y+30,column,ink);
        if(shown.isPresent()){
            g.fill(x,y+43,x+column,y+48,0xFFBCA27D);
            int filled=(int)Math.round(column*Math.clamp(shown.getAsDouble(),0,100)/100.0);if(filled>0)g.fill(x,y+43,x+filled,y+48,0xFF817644);
        }
        line(g,font,Component.translatable("hometown.food.variety.groups",v.qualifyingEnabledGroups(),v.enabledGroups().size()),x,y+60,column,ink);
        line(g,font,Component.translatable("hometown.food.variety.unique",v.uniqueFoodCount()),x,y+75,column,ink);
        if(!v.populationComplete())line(g,font,Component.translatable("hometown.food.variety.population_unknown"),x,y+92,column,warning);

        line(g,font,Component.translatable("hometown.food.variety.stored_groups"),right,y,column,ink);
        int row=0;
        for(var group:FoodGroup.values()) {
            var q=v.qualifications().get(group);String key=switch(q){case TRUE->"present";case FALSE->"below";case UNKNOWN->"unknown";case DISABLED->"excluded";};
            Component amount=Component.translatable(v.sourceStatus()==FoodScanStatus.COMPLETE?"hometown.food.variety.group":"hometown.food.variety.group_known",
                    Component.translatable("hometown.food.variety.group_name."+group.id),String.format(java.util.Locale.ROOT,"%,d",v.perGroupNutrition().get(group)),
                    Component.translatable("hometown.food.variety.qual."+key));
            line(g,font,amount,right,y+15+row*18,column,q==FoodVarietySnapshot.Qualification.UNKNOWN?warning:ink);row++;
        }
        Component required=v.requiredNutritionPerGroup().isPresent()?Component.translatable("hometown.food.variety.required",String.format(java.util.Locale.ROOT,"%,d",v.requiredNutritionPerGroup().getAsLong())):
                Component.translatable("hometown.food.variety.required_na");
        line(g,font,required,right,y+108,column,muted);
    }
    static void growing(GuiGraphics g,Font font,FoodGrowingSnapshot s,int page,int x,int right,int y,int column,int ink,int muted,int warning) {
        line(g,font,Component.translatable("hometown.food.growing.title"),x,y,column,ink);
        boolean partial=s.scanStatus()==FoodGrowingSnapshot.Status.PARTIAL||s.scanStatus()==FoodGrowingSnapshot.Status.UNAVAILABLE;
        line(g,font,Component.translatable(partial?"hometown.food.growing.known_crops":"hometown.food.growing.crops",s.growingCropBlocks()),x,y+16,column,partial?warning:ink);
        if(s.maturityUnassessedCropBlocks()>0){
            line(g,font,Component.translatable("hometown.food.growing.known_mature",s.knownMatureCropBlocks()),x,y+32,column,ink);
            line(g,font,Component.translatable("hometown.food.growing.unassessed",s.maturityUnassessedCropBlocks()),x,y+47,column,warning);
        } else line(g,font,Component.translatable("hometown.food.growing.mature",s.knownMatureCropBlocks()),x,y+32,column,ink);
        line(g,font,Component.translatable("hometown.food.growing.families",s.cropFamilyCount()),x,y+63,column,ink);
        line(g,font,Component.translatable("hometown.food.growing.now_hint"),x,y+81,column,muted);
        if(partial&&!s.reasonCounts().isEmpty()){
            var reason=s.reasonCounts().keySet().stream().filter(r->r!=FoodGrowingSnapshot.Reason.MATURITY_UNASSESSED).sorted().findFirst();
            reason.ifPresent(r->line(g,font,Component.translatable("hometown.food.growing.reason."+r.name().toLowerCase(java.util.Locale.ROOT)),x,y+100,column,warning));
        }
        line(g,font,Component.translatable("hometown.food.growing.family_title"),right,y,column,ink);
        int pages=Math.max(1,(s.families().size()+3)/4),safe=Math.clamp(page,0,pages-1),start=safe*4;
        for(int i=start;i<Math.min(start+4,s.families().size());i++){
            var family=s.families().get(i);int ry=y+17+(i-start)*27;
            line(g,font,Component.literal(label(family.familyId().toString())),right,ry,column,ink);
            Component counts=family.mature().isPresent()?Component.translatable("hometown.food.growing.family_counts",family.growing(),family.mature().getAsInt()):
                    Component.translatable("hometown.food.growing.family_unassessed",family.growing());
            line(g,font,counts,right,ry+12,column,family.mature().isPresent()?muted:warning);
        }
        if(pages>1)line(g,font,Component.translatable("hometown.food.growing.page",safe+1,pages),right,y+126,column,muted);
    }
    private static void line(GuiGraphics g,Font font,Component text,int x,int y,int width,int color){
        String value=text.getString();String shown=font.width(value)>width?font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…":value;g.drawString(font,shown,x,y,color,false);
    }
    private static int round(double value){return (int)Math.floor(value+0.5d);}
    private static String label(String id){int colon=id.indexOf(':');String path=colon>=0?id.substring(colon+1):id;var out=new StringBuilder();for(String word:path.replace('_',' ').split(" ")){if(word.isEmpty())continue;if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
}
