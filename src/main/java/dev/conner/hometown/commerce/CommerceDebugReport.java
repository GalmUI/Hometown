package dev.conner.hometown.commerce;

import java.util.*;

public final class CommerceDebugReport {
    public static final int PAGE_SIZE=32;
    private CommerceDebugReport() {}
    public static String format(CommerceSnapshot snapshot){return format(snapshot,0);}
    public static String format(CommerceSnapshot snapshot,int requestedPage) {
        var out=new StringBuilder("Commerce Debug\n");
        out.append("Status: ").append(snapshot.scanStatus()).append(" | enabled=").append(snapshot.enabled()).append('\n');
        out.append("Reasons: ").append(snapshot.reasonCounts()).append('\n');
        out.append("Population observed: ").append(snapshot.totalResidents()).append('\n');
        out.append("Eligible adults: ").append(snapshot.eligibleAdults()).append(" | employed=").append(snapshot.employedAdults())
                .append(" | unemployed=").append(snapshot.unemployedAdults()).append('\n');
        out.append("Excluded babies: ").append(snapshot.excludedBabies()).append(" | excluded nitwits=").append(snapshot.excludedNitwits()).append('\n');
        out.append("Employment raw: ").append(snapshot.employmentPercent().isPresent()?snapshot.employmentPercent().getAsDouble():
                snapshot.observedEmploymentPercent().isPresent()?snapshot.observedEmploymentPercent().getAsDouble()+" observed":"N/A")
                .append(" | authoritative=").append(snapshot.employmentPercent().isPresent()).append(" | state=").append(snapshot.employmentState()).append('\n');
        out.append("Profession diversity: ").append(snapshot.professionDiversity()).append('\n');
        var professions=new ArrayList<>(snapshot.professionCounts().entrySet());
        professions.sort(Map.Entry.comparingByKey());
        int pages=Math.max(1,(professions.size()+PAGE_SIZE-1)/PAGE_SIZE);
        int page=Math.clamp(requestedPage,0,pages-1),start=page*PAGE_SIZE;
        out.append("Profession detail page: ").append(page).append(" / ").append(pages-1).append(" (32 records max)\n");
        for(int i=start;i<Math.min(start+PAGE_SIZE,professions.size());i++){
            var entry=professions.get(i);out.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }
        out.append("Resident inspections: ").append(snapshot.residentInspections()).append(" | duplicates rejected=").append(snapshot.duplicateResidents()).append('\n');
        var m=snapshot.metadata();
        out.append("Generation: ").append(m.requestGeneration()).append(" | observedGameTime=").append(m.observedGameTime()).append('\n');
        out.append("Revisions: config=").append(m.configurationRevision()).append(" data=").append(m.dataRevision()).append(" bounds=").append(m.boundsRevision());
        return out.toString();
    }
}
