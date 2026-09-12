package dev.conner.hometown.history;

import java.util.*;

/** Zero-scan report over already persisted History state. */
public final class HistoryDebugReport {
    private static final int PAGE_SIZE=32;
    private HistoryDebugReport() {}

    public static String format(HistoryTownState state,int requestedPage) {
        var events=state.newestFirst();int pages=Math.max(1,(events.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.clamp(requestedPage,0,pages-1);
        int start=page*PAGE_SIZE,end=Math.min(start+PAGE_SIZE,events.size());
        var out=new StringBuilder("History Debug\n");
        out.append("Retained events: ").append(events.size()).append(" | nextSequence=").append(state.nextSequenceNumber()).append('\n');
        out.append("Prosperity high-water rank: ").append(state.highestConfirmedProsperityMilestone()).append('\n');
        out.append("Durable domains:\n");
        for(var domain:HistoryTownState.Domain.values()) {
            var d=state.domain(domain);
            out.append("  ").append(domain).append(" | baseline=").append(d.baselineInitialized()?d.baseline():"N/A")
                    .append(" | comparisonRevision=").append(d.comparisonRevision())
                    .append(" | fingerprint=").append(d.fingerprintInitialized()?Long.toString(d.fingerprint()):"N/A").append('\n');
        }
        out.append("Event page: ").append(page).append(" / ").append(pages-1).append(" (32 records max)\n");
        if(events.isEmpty())out.append("  <none>");
        for(int i=start;i<end;i++) {
            var e=events.get(i);out.append("  #").append(e.sequenceNumber()).append(' ').append(e.type())
                    .append(" | day=").append(e.observedDay()).append(" | observed=").append(e.observedGameTime())
                    .append(" | revision=").append(e.configurationRevision()).append(" | args=").append(argumentSummary(e.arguments())).append('\n');
        }
        return out.toString().stripTrailing();
    }

    private static String argumentSummary(Map<String,HistoryArgument> args) {
        var entries=new ArrayList<>(args.entrySet());entries.sort(Map.Entry.comparingByKey());var out=new StringBuilder("{");
        for(int i=0;i<entries.size();i++){if(i>0)out.append(", ");var e=entries.get(i);out.append(e.getKey()).append('=').append(e.getValue().type()).append(':').append(e.getValue().value());}
        return out.append('}').toString();
    }
}
