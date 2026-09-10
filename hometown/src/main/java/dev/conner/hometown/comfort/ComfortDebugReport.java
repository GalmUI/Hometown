package dev.conner.hometown.comfort;

import java.util.*;

public final class ComfortDebugReport {
    private ComfortDebugReport(){}
    public static String format(ComfortSnapshot s,int requestedPage) {
        var rows=new ArrayList<String>();int page=Math.max(0,Math.min(requestedPage,s.roomRecords().size()));
        rows.add("Comfort "+s.scanStatus()+" / "+s.condition()+" enabled="+s.enabled());
        rows.add("Metadata: "+s.metadata());
        rows.add("Reasons: "+new TreeMap<>(s.reasonCounts()));
        rows.add("Rooms assessed="+s.assessedRooms()+"/"+s.expectedRooms()+" beds="+s.assessedEnclosedBeds()+"/"+s.expectedEnclosedBeds());
        rows.add("Room attempts="+s.roomsAttempted()+"/"+s.roomLimit()+" new block reads="+s.blockInspections()+"/"+s.sharedBlockLimit()+" per-room ceiling="+s.cellsPerRoomLimit());
        rows.add("Raw town="+s.residentialComfortPercent()+" observed="+s.observedRoomComfortPercent()+" displayed="+
            (s.residentialComfortPercent().isPresent()?Math.round(s.residentialComfortPercent().getAsDouble()):"N/A")+" band="+s.band());
        rows.add("Page "+page+"/"+s.roomRecords().size()+" (0=categories, 1 onward=room details)");
        if(page==0)for(var c:ComfortCategory.values()) {
            var setting=s.categories().get(c);
            rows.add(c+" "+(setting.enabled()?"ENABLED":"DISABLED")+" weight="+setting.weight()+" coverage="+s.categoryCoverage().getOrDefault(c,0)+"/"+s.assessedRooms());
        } else {
            var room=s.roomRecords().get(page-1);
            rows.add("Room "+room.roomKey().toShortString()+" enclosedBeds="+room.enclosedBeds()+" status="+room.scanStatus());
            rows.add("Cells interior="+room.interiorCellsConsidered()+" boundary="+room.boundaryCellsConsidered());
            rows.add("Room reasons="+new TreeMap<>(room.reasonCounts()));
            rows.add("Raw score="+room.score()+" displayed="+(room.score().isPresent()?Math.round(room.score().getAsDouble()):"N/A")+" band="+room.band());
            rows.add("Weight="+room.presentWeight()+"/"+room.enabledWeight()+" ignoredDuplicates="+room.ignoredDuplicateHits()+
                " stateFailures="+room.statePredicateFailures()+" excluded="+room.excludedBlocks());
            for(var c:ComfortCategory.values())rows.add(c+" "+room.categoryPresence().get(c)+" hits="+room.perCategoryQualifyingHitCount().getOrDefault(c,0)+
                " weight="+s.categories().get(c).weight());
        }
        return String.join("\n",rows);
    }
}
