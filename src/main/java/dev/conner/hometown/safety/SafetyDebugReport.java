package dev.conner.hometown.safety;

import java.util.*;
public final class SafetyDebugReport {
    private SafetyDebugReport() {}
    public static String format(SafetySnapshot s,int page) {
        var rows=new ArrayList<String>();
        rows.add("Safety: entities="+s.entityScanStatus()+" lighting="+s.lightingScanStatus()+" enabled="+s.enabled());
        rows.add("Metadata: "+s.metadata());
        rows.add("Entity reasons: "+new TreeMap<>(s.entityReasonCounts()));
        rows.add("Lighting reasons: "+new TreeMap<>(s.lightingReasonCounts()));
        rows.add("Threats="+s.threatsObserved()+" Protectors="+s.protectorsObserved());
        rows.add("Entity attempts="+s.entitiesInspected()+"/"+s.entityLimit()+" rejected="+s.rejectedEntities()+" duplicates="+s.duplicateEntities());
        rows.add("Loaded chunks="+s.loadedChunks()+"/"+s.requiredChunks());
        rows.add("Lighting expected="+s.expectedBedSamples()+" assessed="+s.assessedBedSamples()+" lit="+s.litBedSamples()+" unlit="+s.unlitBedSamples()+" unassessed="+s.unassessedBedSamples());
        rows.add("Raw observed="+s.observedLightingPercent()+" authoritative="+s.residentialLightingPercent()+" displayed="+(s.residentialLightingPercent().isPresent()?Math.round(s.residentialLightingPercent().getAsDouble()):"N/A"));
        rows.add("Block attempts="+s.blocksInspected()+"/"+s.blockLimit()+" threshold="+s.minimumBlockLight()+(s.minimumBlockLight()==0?" (zero light counts as lit)":""));
        var detail=new ArrayList<String>();
        new TreeMap<>(s.countsByThreatType()).forEach((k,v)->detail.add("Threat "+k+": "+v));
        new TreeMap<>(s.countsByProtectorType()).forEach((k,v)->detail.add("Protector "+k+": "+v));
        new TreeMap<>(s.collisions()).forEach((k,v)->detail.add("Collision "+k+": "+v));
        s.invalidSamples().forEach(p->detail.add("Invalid sample "+p.toShortString()));
        int pages=Math.max(1,(detail.size()+19)/20);
        int start=Math.min(Math.max(0,page),pages-1)*20;
        rows.add("Details page "+(Math.min(Math.max(0,page),pages-1)+1)+"/"+pages+"; invalid sample positions capped at 32");
        rows.addAll(detail.subList(start,Math.min(detail.size(),start+20)));
        return String.join("\n",rows);
    }
}
