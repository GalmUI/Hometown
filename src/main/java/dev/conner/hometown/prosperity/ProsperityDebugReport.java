package dev.conner.hometown.prosperity;

import java.util.Locale;

/** Stable operator explanation of one explicit read-only Prosperity observation. */
public final class ProsperityDebugReport {
    private ProsperityDebugReport() {}
    public static String format(ProsperitySnapshot snapshot) {
        var out=new StringBuilder("Prosperity Debug\n");
        out.append("Status: ").append(snapshot.status()).append(" | enabled=").append(snapshot.enabled()).append('\n');
        out.append("Missing required inputs: ").append(snapshot.missingRequiredInputs()).append('\n');
        out.append("Total enabled weight: ").append(snapshot.totalEnabledWeight()).append('\n');
        out.append("Weighted total: ").append(snapshot.weightedTotal().isPresent()?fmt(snapshot.weightedTotal().getAsDouble()):"N/A").append('\n');
        out.append("Development Index raw: ").append(snapshot.developmentIndex().isPresent()?fmt(snapshot.developmentIndex().getAsDouble()):"N/A");
        if(snapshot.developmentIndex().isPresent())out.append(" | displayed=").append(snapshot.displayedIndex()).append('%');
        out.append(" | band=").append(snapshot.band().map(Enum::name).orElse("N/A")).append('\n');
        out.append("Components:\n");
        for(var c:snapshot.components()) {
            out.append("  ").append(c.type()).append(": status=").append(c.status())
                    .append(" | raw=").append(c.sourceRawValue().isPresent()?fmt(c.sourceRawValue().getAsDouble()):"N/A")
                    .append(" | normalized=").append(c.normalizedValue().isPresent()?fmt(c.normalizedValue().getAsDouble()):"N/A")
                    .append(" | weight=").append(c.configuredWeight())
                    .append(" | contribution=").append(c.weightedContribution().isPresent()?fmt(c.weightedContribution().getAsDouble()):"N/A")
                    .append(" | reasons=").append(c.reasons()).append('\n');
        }
        var m=snapshot.metadata();
        out.append("Generation: ").append(m.requestGeneration()).append(" | observedGameTime=").append(m.observedGameTime()).append('\n');
        out.append("Revisions: config=").append(m.configurationRevision()).append(" data=").append(m.dataRevision()).append(" bounds=").append(m.boundsRevision());
        return out.toString();
    }
    private static String fmt(double value){return String.format(Locale.ROOT,"%.4f",value);}
}
