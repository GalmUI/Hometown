package dev.conner.hometown.commerce;

import java.util.*;

public final class CommerceDebugReport {
    private CommerceDebugReport() {}
    public static String format(CommerceSnapshot snapshot) {
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
        snapshot.professionCounts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->out.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n'));
        out.append("Resident inspections: ").append(snapshot.residentInspections()).append(" | duplicates rejected=").append(snapshot.duplicateResidents()).append('\n');
        var m=snapshot.metadata();
        out.append("Generation: ").append(m.requestGeneration()).append(" | observedGameTime=").append(m.observedGameTime()).append('\n');
        out.append("Revisions: config=").append(m.configurationRevision()).append(" data=").append(m.dataRevision()).append(" bounds=").append(m.boundsRevision());
        return out.toString();
    }
}
